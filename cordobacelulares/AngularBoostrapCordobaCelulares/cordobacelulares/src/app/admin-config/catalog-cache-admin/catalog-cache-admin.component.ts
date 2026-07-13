import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, finalize, interval } from 'rxjs';
import { ApiDateValue, CatalogCacheStatus } from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

export type RefreshUiState = 'idle' | 'starting' | 'refreshing' | 'success' | 'error' | 'cooldown';

@Component({
  selector: 'app-catalog-cache-admin',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './catalog-cache-admin.component.html',
  styleUrl: './catalog-cache-admin.component.css'
})
export class CatalogCacheAdminComponent implements OnInit, OnDestroy {
  status: CatalogCacheStatus | null = null;
  cacheStatusLoading = false;
  cacheRefreshSubmitting = false;
  refreshUiState: RefreshUiState = 'idle';
  error = '';
  message = '';

  private polling?: Subscription;
  private successResetTimer?: ReturnType<typeof setTimeout>;

  constructor(private adminConfigService: AdminConfigService) { }

  ngOnInit(): void {
    this.loadStatus();
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.clearSuccessResetTimer();
  }

  loadStatus(): void {
    this.loadStatusInternal(false);
  }

  private loadStatusInternal(fromPolling: boolean): void {
    if (!fromPolling) {
      this.cacheStatusLoading = true;
    }
    this.error = '';
    this.adminConfigService.getCatalogCacheStatus()
      .pipe(finalize(() => {
        if (!fromPolling) {
          this.cacheStatusLoading = false;
        }
      }))
      .subscribe({
        next: status => {
          this.applyStatus(status);
        },
        error: (error: Error) => {
          this.error = error.message;
          if (fromPolling) {
            this.stopPolling();
          }
        }
      });
  }

  refreshCatalog(): void {
    if (this.cacheRefreshSubmitting || this.status?.refreshing || this.isCooldownActive()) {
      return;
    }

    this.cacheRefreshSubmitting = true;
    this.refreshUiState = 'starting';
    this.error = '';
    this.message = '';
    this.clearSuccessResetTimer();

    this.adminConfigService.refreshCatalogCache()
      .pipe(finalize(() => {
        this.cacheRefreshSubmitting = false;
      }))
      .subscribe({
        next: response => {
          this.status = response.status;
          if (response.blockedByCooldown) {
            this.refreshUiState = 'cooldown';
            this.message = 'Tienda Porte pidio esperar antes de un nuevo intento.';
            this.stopPolling();
            return;
          } else if (response.alreadyRunning) {
            this.refreshUiState = 'refreshing';
            this.message = 'Ya existe una actualizacion en curso.';
          } else if (response.refreshStarted) {
            this.refreshUiState = 'refreshing';
            this.message = 'Actualizacion iniciada en segundo plano.';
          } else {
            this.message = 'No se inicio una nueva actualizacion.';
          }

          if (response.status.refreshing) {
            this.startPolling();
          } else {
            this.stopPolling();
            this.finishRefreshFromStatus(response.status);
          }
        },
        error: (error: Error) => {
          this.error = error.message;
          this.refreshUiState = 'error';
        }
      });
  }

  stateLabel(): string {
    if (!this.status || !this.status.initialized) {
      return 'Sin catalogo';
    }
    if (this.status.refreshing) {
      return 'Actualizando';
    }
    if (this.status.stale) {
      return 'Vencido';
    }
    if (this.status.fresh) {
      return 'Actualizado';
    }
    return 'Sin catalogo';
  }

  stateClass(): string {
    if (!this.status || !this.status.initialized) {
      return 'state-muted';
    }
    if (this.status.refreshing) {
      return 'state-working';
    }
    if (this.status.stale) {
      return 'state-warning';
    }
    return 'state-ok';
  }

  snapshotAge(): string {
    return this.snapshotAgeLabel();
  }

  snapshotAgeLabel(): string {
    const lastSuccess = this.parseApiDate(this.status?.lastSuccessfulRefreshAt);
    if (!lastSuccess) {
      return '—';
    }
    const elapsedSeconds = Math.max(0, Math.floor((Date.now() - lastSuccess.getTime()) / 1000));
    if (elapsedSeconds < 60) {
      return `${elapsedSeconds}s`;
    }
    if (elapsedSeconds < 3600) {
      return `${Math.floor(elapsedSeconds / 60)}m ${elapsedSeconds % 60}s`;
    }
    if (elapsedSeconds < 86400) {
      return `${Math.floor(elapsedSeconds / 3600)}h ${Math.floor((elapsedSeconds % 3600) / 60)}m`;
    }
    return `${Math.floor(elapsedSeconds / 86400)}d ${Math.floor((elapsedSeconds % 86400) / 3600)}h`;
  }

  formatDate(value?: ApiDateValue): string {
    const date = this.parseApiDate(value);
    return date ? date.toLocaleString('es-AR') : '—';
  }

  parseApiDate(value?: ApiDateValue): Date | null {
    if (value === null || value === undefined) {
      return null;
    }

    if (typeof value === 'number') {
      return this.dateFromUnixNumber(value);
    }

    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }

    if (/^-?\d+(\.\d+)?$/.test(trimmed)) {
      return this.dateFromUnixNumber(Number(trimmed));
    }

    return this.validDate(new Date(trimmed));
  }

  singleFlightLabel(): string {
    return this.status?.refreshing ? 'Ocupado' : 'Libre';
  }

  refreshButtonLabel(): string {
    if (this.cacheRefreshSubmitting) {
      return 'Iniciando...';
    }
    if (this.status?.refreshing) {
      return 'Actualizando...';
    }
    if (this.isCooldownActive()) {
      return `Esperar hasta ${this.formatDate(this.status?.nextAllowedRefreshAt)}`;
    }
    return 'Actualizar catalogo';
  }

  isRefreshDisabled(): boolean {
    return this.cacheRefreshSubmitting || !!this.status?.refreshing || this.isCooldownActive();
  }

  isCooldownActive(): boolean {
    const nextAllowed = this.parseApiDate(this.status?.nextAllowedRefreshAt);
    return !!nextAllowed && nextAllowed.getTime() > Date.now();
  }

  private applyStatus(status: CatalogCacheStatus): void {
    const wasRefreshing = !!this.status?.refreshing || this.refreshUiState === 'refreshing';
    this.status = status;

    if (status.refreshing) {
      this.refreshUiState = 'refreshing';
      this.message = 'Actualizacion iniciada en segundo plano.';
      this.startPolling();
      return;
    }

    this.stopPolling();
    if (wasRefreshing) {
      this.finishRefreshFromStatus(status);
    }
  }

  private finishRefreshFromStatus(status: CatalogCacheStatus): void {
    if (status.lastError) {
      this.refreshUiState = 'error';
      this.message = status.initialized
        ? 'No se pudo actualizar. Se mantiene el catalogo anterior.'
        : 'No se pudo actualizar el catalogo.';
      return;
    }
    this.refreshUiState = 'success';
    this.message = 'Catalogo actualizado correctamente.';
    this.scheduleSuccessReset();
  }

  private startPolling(): void {
    if (this.polling) {
      return;
    }
    this.polling = interval(2000).subscribe(() => this.loadStatusInternal(true));
  }

  private stopPolling(): void {
    this.polling?.unsubscribe();
    this.polling = undefined;
  }

  private dateFromUnixNumber(value: number): Date | null {
    if (!Number.isFinite(value)) {
      return null;
    }
    const millis = Math.abs(value) < 1_000_000_000_000 ? value * 1000 : value;
    return this.validDate(new Date(millis));
  }

  private validDate(date: Date): Date | null {
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private scheduleSuccessReset(): void {
    this.clearSuccessResetTimer();
    this.successResetTimer = setTimeout(() => {
      if (this.refreshUiState === 'success') {
        this.refreshUiState = 'idle';
      }
    }, 5000);
  }

  private clearSuccessResetTimer(): void {
    if (this.successResetTimer) {
      clearTimeout(this.successResetTimer);
      this.successResetTimer = undefined;
    }
  }
}
