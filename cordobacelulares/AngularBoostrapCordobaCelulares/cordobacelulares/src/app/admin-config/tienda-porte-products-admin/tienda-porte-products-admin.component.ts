import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subject, Subscription, catchError, debounceTime, distinctUntilChanged, finalize, merge, of, startWith, switchMap, tap } from 'rxjs';
import Swal from 'sweetalert2';
import {
  AdminCatalogPageResponse,
  AdminCatalogProductRow,
  AdminCatalogQuery,
  BlockedCatalogProductResponse,
  ProductSortField
} from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

type AdminProductsView = 'general' | 'blocked';

interface SelectOption {
  label: string;
  value: string;
}

interface PaymentTooltipState {
  row: AdminCatalogProductRow;
  left: number;
  top: number;
}

interface PaymentTooltipLine {
  label: string;
  value: string;
}

@Component({
  selector: 'app-tienda-porte-products-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './tienda-porte-products-admin.component.html',
  styleUrl: './tienda-porte-products-admin.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TiendaPorteProductsAdminComponent implements OnInit, OnDestroy {
  readonly searchControl = new FormControl<string>('', { nonNullable: true });
  readonly categoryControl = new FormControl<string>('', { nonNullable: true });
  readonly stockControl = new FormControl<string>('todos', { nonNullable: true });
  readonly limitControl = new FormControl<number>(50, { nonNullable: true });

  readonly categories: SelectOption[] = [
    { label: 'Todas', value: '' },
    { label: 'iPhone', value: 'IPHONE' },
    { label: 'Productos Apple', value: 'PRODUCTOS APPLE' },
    { label: 'Samsung', value: 'SAMSUNG' },
    { label: 'Xiaomi', value: 'XIAOMI' },
    { label: 'Motorola', value: 'MOTOROLA' },
    { label: 'Realme', value: 'REALME' },
    { label: 'Infinix', value: 'INFINIX' },
    { label: 'Huawei', value: 'HUAWEI' },
    { label: 'Honor', value: 'HONOR' },
    { label: 'Oppo', value: 'OPPO' },
    { label: 'Articulos varios', value: 'ARTICULOS VARIOS' },
    { label: 'Perfumes', value: 'PERFUMES' }
  ];

  readonly stockOptions: SelectOption[] = [
    { label: 'Todo stock', value: 'todos' },
    { label: 'Con stock', value: 'con-stock' },
    { label: 'Sin stock', value: 'sin-stock' },
    { label: 'Sin dato', value: 'sin-dato' }
  ];

  readonly limitOptions = [25, 50, 100];

  activeView: AdminProductsView = 'general';
  response: AdminCatalogPageResponse | null = null;
  loading = false;
  error = '';
  page = 1;
  sort: ProductSortField = 'marca';
  direction: 'asc' | 'desc' = 'asc';

  blockedProducts: BlockedCatalogProductResponse[] = [];
  blockedLoading = false;
  blockedError = '';
  blockingProductId: number | null = null;
  unblockingProductId: number | null = null;
  paymentTooltip: PaymentTooltipState | null = null;

  private readonly reload$ = new Subject<void>();
  private subscription?: Subscription;
  private blockedSubscription?: Subscription;

  constructor(
    private adminConfigService: AdminConfigService,
    private changeDetectorRef: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    const filterChanges$ = merge(
      this.searchControl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.categoryControl.valueChanges,
      this.stockControl.valueChanges,
      this.limitControl.valueChanges
    ).pipe(tap(() => {
      this.page = 1;
    }));

    this.subscription = merge(filterChanges$, this.reload$)
      .pipe(
        startWith(null),
        tap(() => {
          this.loading = true;
          this.error = '';
          this.changeDetectorRef.markForCheck();
        }),
        switchMap(() => this.adminConfigService.getAdminCatalogProducts(this.buildQuery()).pipe(
          catchError((error: Error) => {
            this.error = error.message;
            return of(null);
          })
        ))
      )
      .subscribe(response => {
        this.response = response;
        this.loading = false;
        this.changeDetectorRef.markForCheck();
      });

    this.loadBlockedProducts();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.blockedSubscription?.unsubscribe();
    this.reload$.complete();
  }

  setActiveView(view: AdminProductsView): void {
    if (this.activeView === view) {
      return;
    }
    this.activeView = view;
    this.hidePaymentTooltip();
    if (view === 'blocked') {
      this.loadBlockedProducts();
    }
    this.changeDetectorRef.markForCheck();
  }

  refresh(): void {
    this.hidePaymentTooltip();
    if (this.activeView === 'blocked') {
      this.loadBlockedProducts();
      return;
    }
    this.reload$.next();
  }

  goToPage(page: number): void {
    if (page < 1 || (this.response && page > this.response.totalPages)) {
      return;
    }
    this.page = page;
    this.reload$.next();
  }

  changeSort(field: ProductSortField): void {
    if (this.sort === field) {
      this.direction = this.direction === 'asc' ? 'desc' : 'asc';
    } else {
      this.sort = field;
      this.direction = 'asc';
    }
    this.reload$.next();
  }

  sortIcon(field: ProductSortField): string {
    if (this.sort !== field) {
      return 'bi-arrow-down-up';
    }
    return this.direction === 'asc' ? 'bi-sort-up' : 'bi-sort-down';
  }

  formatMoney(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return '-';
    }
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 0
    }).format(value);
  }

  formatMoneyOrDash(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return '—';
    }
    return this.formatMoney(value);
  }

  formatUsd(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return '-';
    }
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2
    }).format(value);
  }

  installment(value: number | null | undefined, count: number): string {
    if (!this.isFiniteNumber(value) || count <= 0) {
      return '-';
    }
    return this.formatMoney(value / count);
  }

  installmentOrDash(value: number | null | undefined, count: number): string {
    if (!this.isFiniteNumber(value) || count <= 0) {
      return '—';
    }
    return this.formatMoney(value / count);
  }

  stockLabel(row: AdminCatalogProductRow): string {
    if (row.cantidad === null || row.cantidad === undefined) {
      return 'Sin dato';
    }
    if (row.cantidad === 1) {
      return '1 unidad';
    }
    return `${row.cantidad} unidades`;
  }

  providerLabel(product: BlockedCatalogProductResponse): string {
    return product.origen === 'TIENDA_PORTE' ? 'Tienda Porte' : product.origen;
  }

  canBlock(row: AdminCatalogProductRow): boolean {
    return typeof row.id === 'number' && row.id > 0;
  }

  blockButtonTitle(row: AdminCatalogProductRow): string {
    return this.canBlock(row)
      ? 'Bloquear producto'
      : 'No se puede bloquear porque el proveedor no informo un ID';
  }

  block(row: AdminCatalogProductRow): void {
    if (!this.canBlock(row) || row.id === null) {
      void Swal.fire({
        icon: 'warning',
        title: 'Producto sin ID',
        text: 'No se puede bloquear porque el proveedor no informo un ID.'
      });
      return;
    }

    void Swal.fire({
      icon: 'warning',
      title: 'Bloquear producto',
      text: `El producto "${row.modelo}" dejara de mostrarse en el catalogo publico.`,
      showCancelButton: true,
      confirmButtonText: 'Bloquear',
      cancelButtonText: 'Cancelar'
    }).then(result => {
      if (!result.isConfirmed || row.id === null) {
        return;
      }
      this.blockingProductId = row.id;
      this.changeDetectorRef.markForCheck();
      this.adminConfigService.blockCatalogProduct(row.id)
        .pipe(finalize(() => {
          this.blockingProductId = null;
          this.changeDetectorRef.markForCheck();
        }))
        .subscribe({
          next: () => {
            this.reload$.next();
            this.loadBlockedProducts();
            void Swal.fire({
              icon: 'success',
              title: 'Producto bloqueado',
              timer: 1400,
              showConfirmButton: false
            });
          },
          error: (error: Error) => this.showActionError('No se pudo bloquear el producto', error)
        });
    });
  }

  unblock(product: BlockedCatalogProductResponse): void {
    if (!this.canUnblock(product)) {
      void Swal.fire({
        icon: 'warning',
        title: 'Producto sin ID',
        text: 'No se puede quitar el bloqueo porque falta el ID externo.'
      });
      return;
    }

    void Swal.fire({
      icon: 'warning',
      title: 'Quitar bloqueo',
      text: `El producto "${product.modelo}" podra volver a mostrarse si sigue disponible.`,
      showCancelButton: true,
      confirmButtonText: 'Quitar bloqueo',
      cancelButtonText: 'Cancelar'
    }).then(result => {
      if (!result.isConfirmed) {
        return;
      }
      this.unblockingProductId = product.id;
      this.changeDetectorRef.markForCheck();
      this.adminConfigService.unblockCatalogProduct(product.id)
        .pipe(finalize(() => {
          this.unblockingProductId = null;
          this.changeDetectorRef.markForCheck();
        }))
        .subscribe({
          next: () => {
            this.blockedProducts = this.blockedProducts.filter(blocked => blocked.id !== product.id);
            this.reload$.next();
            this.loadBlockedProducts();
            void Swal.fire({
              icon: 'success',
              title: 'Bloqueo quitado',
              timer: 1400,
              showConfirmButton: false
            });
          },
          error: (error: Error) => this.showActionError('No se pudo quitar el bloqueo', error)
        });
    });
  }

  showPaymentTooltip(row: AdminCatalogProductRow, event: Event): void {
    const target = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    if (!target) {
      return;
    }
    const rect = target.getBoundingClientRect();
    const tooltipWidth = 280;
    const left = Math.min(Math.max(12, rect.left), Math.max(12, window.innerWidth - tooltipWidth - 12));
    const top = Math.min(rect.bottom + 8, Math.max(12, window.innerHeight - 180));
    this.paymentTooltip = { row, left, top };
    this.changeDetectorRef.markForCheck();
  }

  togglePaymentTooltip(row: AdminCatalogProductRow, event: Event): void {
    if (this.paymentTooltip?.row === row) {
      this.hidePaymentTooltip();
      return;
    }
    this.showPaymentTooltip(row, event);
  }

  hidePaymentTooltip(): void {
    if (!this.paymentTooltip) {
      return;
    }
    this.paymentTooltip = null;
    this.changeDetectorRef.markForCheck();
  }

  paymentTooltipLines(row: AdminCatalogProductRow): PaymentTooltipLine[] {
    return [
      { label: 'Transferencia', value: this.formatMoneyOrDash(row.precioTransferenciaBancaria) },
      { label: '3 cuotas', value: this.installmentOrDash(row.precioTarjeta3Pagos, 3) },
      { label: '6 cuotas', value: this.installmentOrDash(row.precioTarjeta6Pagos, 6) },
      { label: '12 cuotas', value: this.installmentOrDash(row.precioTarjeta12Pagos, 12) }
    ];
  }

  trackByRow(_: number, row: AdminCatalogProductRow): string {
    return `${row.id ?? 'sin-id'}|${row.marca}|${row.modelo}|${row.color}|${row.precioUsd ?? 'sin-precio'}`;
  }

  trackByBlockedProduct(_: number, product: BlockedCatalogProductResponse): number {
    return product.id;
  }

  private loadBlockedProducts(): void {
    this.blockedSubscription?.unsubscribe();
    this.blockedLoading = true;
    this.blockedError = '';
    this.changeDetectorRef.markForCheck();
    this.blockedSubscription = this.adminConfigService.getBlockedCatalogProducts()
      .pipe(
        catchError((error: Error) => {
          this.blockedError = error.message;
          return of([]);
        })
      )
      .subscribe(products => {
        this.blockedProducts = products;
        this.blockedLoading = false;
        this.changeDetectorRef.markForCheck();
      });
  }

  private canUnblock(product: BlockedCatalogProductResponse): boolean {
    return typeof product.id === 'number' && product.id > 0;
  }

  private showActionError(title: string, error: Error): void {
    void Swal.fire({
      icon: 'error',
      title,
      text: error.message || 'No se pudo completar la operacion.'
    });
  }

  private isFiniteNumber(value: number | null | undefined): value is number {
    return typeof value === 'number' && Number.isFinite(value);
  }

  private buildQuery(): AdminCatalogQuery {
    const texto = this.searchControl.value.trim();
    const categoria = this.categoryControl.value;
    return {
      texto: texto || undefined,
      categoria: categoria || undefined,
      page: this.page,
      limit: this.limitControl.value,
      sort: this.sort,
      direction: this.direction,
      origen: 'TIENDA_PORTE',
      stock: this.stockControl.value
    };
  }
}
