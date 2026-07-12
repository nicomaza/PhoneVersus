import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { CatalogCacheStatus } from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

@Component({
  selector: 'app-catalog-cache-admin',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './catalog-cache-admin.component.html',
  styleUrl: './catalog-cache-admin.component.css'
})
export class CatalogCacheAdminComponent implements OnInit, OnDestroy {
  status: CatalogCacheStatus | null = null;
  loading = false;
  refreshing = false;
  error = '';
  message = '';

  private polling?: Subscription;

  constructor(private adminConfigService: AdminConfigService) { }

  ngOnInit(): void {
    this.loadStatus();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  loadStatus(): void {
    this.loading = true;
    this.error = '';
    this.adminConfigService.getCatalogCacheStatus().subscribe({
      next: status => {
        this.status = status;
        if (status.refreshing) {
          this.startPolling();
        } else {
          this.stopPolling();
        }
      },
      error: (error: Error) => {
        this.error = error.message;
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  refreshCatalog(): void {
    if (this.refreshing || this.status?.refreshing) {
      return;
    }

    this.refreshing = true;
    this.error = '';
    this.message = '';

    this.adminConfigService.refreshCatalogCache().subscribe({
      next: response => {
        this.status = response.status;
        if (response.blockedByCooldown) {
          this.message = 'Tienda Porte pidio esperar antes de un nuevo intento.';
        } else if (response.alreadyRunning) {
          this.message = 'Ya hay una actualizacion del catalogo en curso.';
        } else if (response.refreshStarted) {
          this.message = 'Actualizacion iniciada en segundo plano.';
        } else {
          this.message = 'No se inicio una nueva actualizacion.';
        }

        if (response.status.refreshing) {
          this.startPolling();
        } else {
          this.stopPolling();
        }
      },
      error: (error: Error) => {
        this.error = error.message;
        this.refreshing = false;
      },
      complete: () => {
        this.refreshing = false;
      }
    });
  }

  stateLabel(): string {
    if (!this.status?.initialized) {
      return 'Sin snapshot';
    }
    if (this.status.refreshing) {
      return 'Actualizando';
    }
    if (this.status.stale) {
      return 'Vencido';
    }
    return 'Vigente';
  }

  stateClass(): string {
    if (!this.status?.initialized) {
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
    if (!this.status?.lastSuccessfulRefreshAt) {
      return '-';
    }
    const lastSuccess = new Date(this.status.lastSuccessfulRefreshAt).getTime();
    const elapsedSeconds = Math.max(0, Math.floor((Date.now() - lastSuccess) / 1000));
    if (elapsedSeconds < 60) {
      return `${elapsedSeconds}s`;
    }
    const elapsedMinutes = Math.floor(elapsedSeconds / 60);
    return `${elapsedMinutes}m ${elapsedSeconds % 60}s`;
  }

  formatDate(value: string | null | undefined): string {
    if (!value) {
      return '-';
    }
    return new Date(value).toLocaleString('es-AR');
  }

  private startPolling(): void {
    if (this.polling) {
      return;
    }
    this.polling = interval(2000).subscribe(() => this.loadStatus());
  }

  private stopPolling(): void {
    this.polling?.unsubscribe();
    this.polling = undefined;
  }
}
