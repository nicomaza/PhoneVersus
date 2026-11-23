import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, debounceTime, distinctUntilChanged, finalize, switchMap, timer } from 'rxjs';
import {
  ApiDateValue,
  PorteBulkPublishPreview,
  PorteBulkPublishResponse,
  PorteProductSyncStatus,
  ProductContentListItem
} from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

@Component({
  selector: 'app-product-content-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './product-content-admin.component.html',
  styleUrl: './product-content-admin.component.css'
})
export class ProductContentAdminComponent implements OnInit, OnDestroy {
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly states = [
    { value: 'ALL', label: 'Todos' },
    { value: 'NONE', label: 'Sin contenido' },
    { value: 'DRAFT', label: 'Borrador' },
    { value: 'PUBLISHED', label: 'Publicado' }
  ];

  products: ProductContentListItem[] = [];
  brands: string[] = [];
  brand = '';
  state = 'ALL';
  page = 1;
  readonly limit = 25;
  total = 0;
  totalPages = 0;
  loading = false;
  error = '';
  syncStatus: PorteProductSyncStatus | null = null;
  syncStarting = false;
  syncError = '';
  syncMessage = '';
  publishPreview: PorteBulkPublishPreview | null = null;
  publishModalOpen = false;
  publishPreviewLoading = false;
  bulkPublishing = false;
  bulkPublishError = '';
  bulkPublishResult: PorteBulkPublishResponse | null = null;

  private readonly subscriptions = new Subscription();
  private syncWasRunning = false;

  constructor(private service: AdminConfigService) {}

  ngOnInit(): void {
    this.subscriptions.add(
      this.searchControl.valueChanges
        .pipe(debounceTime(250), distinctUntilChanged())
        .subscribe(() => {
          this.page = 1;
          this.load();
        })
    );
    this.load();
    this.subscriptions.add(
      timer(0, 3000).pipe(
        switchMap(() => this.service.getPorteProductContentSyncStatus())
      ).subscribe({
        next: status => {
          const justFinished = this.syncWasRunning && !status.running;
          this.syncStatus = status;
          this.syncWasRunning = status.running;
          if (justFinished) {
            this.syncMessage = 'Sincronización de Tienda Porte finalizada.';
            this.load();
          }
        },
        error: (error: Error) => this.syncError = error.message
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.subscriptions.add(
      this.service.getProductContents({
        text: this.searchControl.value.trim(),
        brand: this.brand,
        state: this.state,
        page: this.page,
        limit: this.limit
      }).pipe(finalize(() => this.loading = false)).subscribe({
        next: response => {
          this.products = response.data;
          this.brands = response.brands;
          this.total = response.total;
          this.totalPages = response.totalPages;
        },
        error: (error: Error) => this.error = error.message
      })
    );
  }

  changeBrand(value: string): void {
    this.brand = value;
    this.page = 1;
    this.load();
  }

  changeState(value: string): void {
    this.state = value;
    this.page = 1;
    this.load();
  }

  previousPage(): void {
    if (this.page <= 1) return;
    this.page--;
    this.load();
  }

  nextPage(): void {
    if (this.page >= this.totalPages) return;
    this.page++;
    this.load();
  }

  startPorteSync(): void {
    if (this.syncStarting || this.syncStatus?.running) return;
    this.syncStarting = true;
    this.syncError = '';
    this.syncMessage = '';
    this.subscriptions.add(this.service.startPorteProductContentSync()
      .pipe(finalize(() => this.syncStarting = false))
      .subscribe({
        next: response => {
          this.syncStatus = response.status;
          this.syncWasRunning = response.status.running;
          this.syncMessage = response.message;
        },
        error: (error: Error) => this.syncError = error.message
      }));
  }

  openPortePublishPreview(): void {
    if (this.publishPreviewLoading || this.bulkPublishing) return;
    this.publishPreviewLoading = true;
    this.bulkPublishError = '';
    this.bulkPublishResult = null;
    this.subscriptions.add(this.service.getPorteBulkPublishPreview()
      .pipe(finalize(() => this.publishPreviewLoading = false))
      .subscribe({
        next: preview => {
          this.publishPreview = preview;
          this.publishModalOpen = true;
        },
        error: (error: Error) => this.bulkPublishError = error.message
      }));
  }

  closePortePublishModal(): void {
    if (this.bulkPublishing) return;
    this.publishModalOpen = false;
    this.publishPreview = null;
  }

  publishPorteSheets(): void {
    if (this.bulkPublishing || !this.publishPreview || this.publishPreview.eligibleToPublish <= 0) return;
    this.bulkPublishing = true;
    this.bulkPublishError = '';
    this.subscriptions.add(this.service.publishPorteProductContents()
      .pipe(finalize(() => this.bulkPublishing = false))
      .subscribe({
        next: result => {
          this.bulkPublishResult = result;
          this.publishModalOpen = false;
          this.publishPreview = null;
          this.load();
        },
        error: (error: Error) => this.bulkPublishError = error.message
      }));
  }

  pendingProducts(): number {
    if (!this.syncStatus) return 0;
    return Math.max(0, this.syncStatus.totalProducts - this.syncStatus.processedProducts);
  }

  syncProgress(): number {
    const status = this.syncStatus;
    if (!status || status.totalProducts <= 0) return 0;
    return Math.min(100, Math.round(status.processedProducts * 100 / status.totalProducts));
  }

  syncStateLabel(): string {
    if (!this.syncStatus) return 'Sin información';
    if (this.syncStatus.running) return 'Sincronizando';
    const labels: Record<string, string> = {
      IDLE: 'Sin ejecuciones recientes',
      COMPLETED: 'Completada',
      COMPLETED_WITH_ERRORS: 'Completada con errores',
      FAILED: 'Fallida'
    };
    return labels[this.syncStatus.state] ?? this.syncStatus.state;
  }

  automaticIntervalLabel(): string {
    const interval = this.syncStatus?.automaticIntervalMs ?? 0;
    if (interval <= 0) return '—';
    const hours = interval / 3_600_000;
    return Number.isInteger(hours) ? `cada ${hours} h` : `cada ${Math.round(interval / 60_000)} min`;
  }

  statusLabel(item: ProductContentListItem): string {
    if (item.status === 'PUBLISHED') return item.hasUnpublishedChanges ? 'Publicado · cambios pendientes' : 'Publicado';
    if (item.status === 'DRAFT') return 'Borrador';
    return 'Sin contenido';
  }

  statusClass(item: ProductContentListItem): string {
    if (item.status === 'PUBLISHED') return 'status-published';
    if (item.status === 'DRAFT') return 'status-draft';
    return 'status-empty';
  }

  formatDate(value: ApiDateValue): string {
    if (!value) return '—';
    const numericValue = typeof value === 'number'
      ? value
      : (/^-?\d+(\.\d+)?$/.test(value.trim()) ? Number(value) : null);
    const normalizedValue = numericValue === null
      ? value
      : (Math.abs(numericValue) < 1_000_000_000_000 ? numericValue * 1000 : numericValue);
    const date = new Date(normalizedValue);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('es-AR', {
      timeZone: 'America/Argentina/Buenos_Aires'
    });
  }
}
