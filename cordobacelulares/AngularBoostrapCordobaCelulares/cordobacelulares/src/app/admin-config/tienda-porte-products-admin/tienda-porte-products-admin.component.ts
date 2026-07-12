import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subject, Subscription, catchError, debounceTime, distinctUntilChanged, merge, of, startWith, switchMap, tap } from 'rxjs';
import {
  AdminCatalogPageResponse,
  AdminCatalogProductRow,
  AdminCatalogQuery,
  ProductSortField
} from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

interface SelectOption {
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

  response: AdminCatalogPageResponse | null = null;
  loading = false;
  error = '';
  page = 1;
  sort: ProductSortField = 'marca';
  direction: 'asc' | 'desc' = 'asc';

  private readonly reload$ = new Subject<void>();
  private subscription?: Subscription;

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
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.reload$.complete();
  }

  refresh(): void {
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
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '-';
    }
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 0
    }).format(value);
  }

  formatUsd(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '-';
    }
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2
    }).format(value);
  }

  installment(value: number | null | undefined, count: number): string {
    if (value === null || value === undefined || Number.isNaN(value) || count <= 0) {
      return '-';
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

  trackByRow(_: number, row: AdminCatalogProductRow): string {
    return `${row.id ?? 'sin-id'}|${row.marca}|${row.modelo}|${row.color}|${row.precioUsd ?? 'sin-precio'}`;
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
