import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subscription, catchError, debounceTime, distinctUntilChanged, forkJoin, merge, of } from 'rxjs';
import {
  ApiDateValue,
  ComparativaMetric,
  ComparativaOverallStatus,
  ComparativaProduct,
  ComparativaResponse,
  LiberadosYaCatalogStatus
} from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

type FilterStatus = 'todos' | 'barato' | 'caro' | 'sin-comparativa';
type AvailabilityFilter = 'todos' | 'comparables' | 'incompletos';
type SortOption = 'ventaja' | 'desventaja' | 'marca' | 'modelo' | 'precio-propio' | 'precio-competencia';

interface SelectOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-comparativa',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './comparativa.component.html',
  styleUrl: './comparativa.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ComparativaComponent implements OnInit, OnDestroy {
  private static readonly CATALOG_STALE_AFTER_MS = 6 * 60 * 60 * 1000;

  readonly searchControl = new FormControl<string>('', { nonNullable: true });
  readonly brandControl = new FormControl<string>('', { nonNullable: true });
  readonly statusControl = new FormControl<FilterStatus>('todos', { nonNullable: true });
  readonly availabilityControl = new FormControl<AvailabilityFilter>('todos', { nonNullable: true });
  readonly sortControl = new FormControl<SortOption>('ventaja', { nonNullable: true });

  readonly statusOptions: SelectOption[] = [
    { label: 'Todos', value: 'todos' },
    { label: 'Yo más barato', value: 'barato' },
    { label: 'Yo más caro', value: 'caro' },
    { label: 'Sin comparativa', value: 'sin-comparativa' }
  ];
  readonly availabilityOptions: SelectOption[] = [
    { label: 'Todas', value: 'todos' },
    { label: 'Con comparativa', value: 'comparables' },
    { label: 'Sin comparativa', value: 'incompletos' }
  ];
  readonly sortOptions: SelectOption[] = [
    { label: 'Mayor ventaja mía', value: 'ventaja' },
    { label: 'Mayor desventaja mía', value: 'desventaja' },
    { label: 'Marca', value: 'marca' },
    { label: 'Modelo', value: 'modelo' },
    { label: 'Mi precio', value: 'precio-propio' },
    { label: 'Precio competencia', value: 'precio-competencia' }
  ];

  response: ComparativaResponse | null = null;
  catalogStatus: LiberadosYaCatalogStatus | null = null;
  rows: ComparativaProduct[] = [];
  comparableRows: ComparativaProduct[] = [];
  withoutComparisonRows: ComparativaProduct[] = [];
  brandOptions: SelectOption[] = [{ label: 'Todas', value: '' }];
  loading = false;
  error = '';
  catalogStatusUnavailable = false;

  private readonly subscription = new Subscription();

  constructor(
    private adminConfigService: AdminConfigService,
    private changeDetectorRef: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    this.subscription.add(
      merge(
        this.searchControl.valueChanges.pipe(debounceTime(160), distinctUntilChanged()),
        this.brandControl.valueChanges,
        this.statusControl.valueChanges,
        this.availabilityControl.valueChanges,
        this.sortControl.valueChanges
      ).subscribe(() => {
        this.applyFilters();
        this.changeDetectorRef.markForCheck();
      })
    );
    this.load();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.catalogStatusUnavailable = false;
    this.changeDetectorRef.markForCheck();

    this.subscription.add(
      forkJoin({
        comparison: this.adminConfigService.getComparativa(),
        catalogStatus: this.adminConfigService.getLiberadosYaCatalogStatus().pipe(
          catchError(() => {
            this.catalogStatusUnavailable = true;
            return of(null);
          })
        )
      }).subscribe({
        next: ({ comparison, catalogStatus }) => {
          this.response = comparison;
          this.catalogStatus = catalogStatus;
          this.rows = Array.isArray(comparison.products) ? comparison.products : [];
          this.brandOptions = this.buildBrandOptions(this.rows);
          this.applyFilters();
          this.loading = false;
          this.changeDetectorRef.markForCheck();
        },
        error: (error: Error) => {
          this.error = error.message || 'No se pudo cargar la comparativa.';
          this.response = null;
          this.catalogStatus = null;
          this.rows = [];
          this.comparableRows = [];
          this.withoutComparisonRows = [];
          this.loading = false;
          this.changeDetectorRef.markForCheck();
        }
      })
    );
  }

  clearFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.brandControl.setValue('', { emitEvent: false });
    this.statusControl.setValue('todos', { emitEvent: false });
    this.availabilityControl.setValue('todos', { emitEvent: false });
    this.sortControl.setValue('ventaja', { emitEvent: false });
    this.applyFilters();
    this.changeDetectorRef.markForCheck();
  }

  estimatedCompetitionRate(metric: ComparativaMetric | null | undefined): string {
    return this.formatRate(metric?.median);
  }

  metricProductCount(metric: ComparativaMetric | null | undefined): string {
    const count = metric?.count ?? 0;
    return count > 0 ? `${count} modelos válidos` : 'Sin modelos válidos';
  }

  formatMoney(value: number | null | undefined): string {
    return this.isPositiveNumber(value) ? this.arsFormatter().format(value) : 'No disponible';
  }

  formatUsd(value: number | null | undefined): string {
    if (!this.isPositiveNumber(value)) {
      return 'No disponible';
    }
    return `USD ${new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(value)}`;
  }

  formatDifference(value: number | null | undefined, currency: 'ARS' | 'USD'): string {
    if (!this.isFiniteNumber(value)) {
      return 'No disponible';
    }
    if (value === 0) {
      return currency === 'USD' ? 'USD 0' : this.arsFormatter().format(0);
    }
    const amount = currency === 'USD'
      ? `USD ${new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(Math.abs(value))}`
      : this.arsFormatter().format(Math.abs(value));
    return `${value > 0 ? '+' : '-'}${amount}`;
  }

  formatPercent(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return 'Sin datos';
    }
    return `${new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)}%`;
  }

  formatRate(value: number | null | undefined): string {
    if (!this.isPositiveNumber(value)) {
      return 'Sin datos';
    }
    return `${new Intl.NumberFormat('es-AR', {
      style: 'currency', currency: 'ARS', minimumFractionDigits: 2, maximumFractionDigits: 2
    }).format(value)} / USD`;
  }

  formatCatalogDate(): string {
    const date = this.catalogDate();
    return date
      ? new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' }).format(date)
      : 'No disponible';
  }

  catalogIsStale(): boolean {
    if (this.catalogStatusUnavailable || !this.catalogStatus?.available || this.catalogStatus.last_error) {
      return true;
    }
    const date = this.catalogDate();
    return !date || Date.now() - date.getTime() > ComparativaComponent.CATALOG_STALE_AFTER_MS;
  }

  catalogWarning(): string {
    if (this.catalogStatusUnavailable) {
      return 'No se pudo verificar el estado del catálogo de LiberadosYa.';
    }
    if (!this.catalogStatus?.available) {
      return 'El catálogo de LiberadosYa no está disponible.';
    }
    if (this.catalogStatus.last_error) {
      return 'La última actualización del catálogo falló; se conserva el último catálogo válido.';
    }
    return 'El catálogo de LiberadosYa está desactualizado.';
  }

  comparisonResult(row: ComparativaProduct): string {
    const difference = row.differences.cashArs;
    if (!this.isFiniteNumber(difference)) {
      return 'No disponible';
    }
    if (difference === 0) {
      return 'Mismo precio';
    }
    const amount = this.arsFormatter().format(Math.abs(difference));
    return difference > 0 ? `Yo más barato por ${amount}` : `Yo más caro por ${amount}`;
  }

  differenceClass(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return 'is-unavailable';
    }
    return value > 0 ? 'is-cheaper' : value < 0 ? 'is-expensive' : 'is-equal';
  }

  globalResultStatus(): ComparativaOverallStatus {
    const status = this.response?.summary.resultStatus;
    if (status) {
      return status;
    }
    const cheaper = this.response?.summary.mineCheaperCount ?? 0;
    const expensive = this.response?.summary.mineMoreExpensiveCount ?? 0;
    if (cheaper > 0 && expensive > 0) {
      return 'MIXED';
    }
    if (cheaper > 0) {
      return 'CHEAPER_ALL';
    }
    if (expensive > 0) {
      return 'MORE_EXPENSIVE_ALL';
    }
    return 'INCOMPLETE';
  }

  globalMixedValue(): string {
    const status = this.globalResultStatus();
    return status === 'INCOMPLETE' ? 'Sin datos' : status === 'MIXED' ? 'Sí' : 'No';
  }

  globalResultLabel(): string {
    return {
      MIXED: 'Hay modelos más baratos y otros más caros',
      CHEAPER_ALL: 'Yo más barato en los modelos comparables',
      MORE_EXPENSIVE_ALL: 'Yo más caro en los modelos comparables',
      COMPETITIVE: 'Mismo precio en los modelos comparables',
      INCOMPLETE: 'Sin modelos comparables'
    }[this.globalResultStatus()] ?? 'Sin modelos comparables';
  }

  matchLabel(row: ComparativaProduct): string {
    return row.matchMessage || 'Sin coincidencia válida en LiberadosYa';
  }

  showComparableSection(): boolean {
    return this.statusControl.value !== 'sin-comparativa' && this.availabilityControl.value !== 'incompletos';
  }

  showWithoutComparisonSection(): boolean {
    return this.statusControl.value !== 'barato'
      && this.statusControl.value !== 'caro'
      && this.availabilityControl.value !== 'comparables';
  }

  visibleCount(): number {
    return (this.showComparableSection() ? this.comparableRows.length : 0)
      + (this.showWithoutComparisonSection() ? this.withoutComparisonRows.length : 0);
  }

  trackByRow(_: number, row: ComparativaProduct): string {
    return `${row.brand}|${row.model}|${row.origin ?? ''}|${row.mine.costUsd ?? ''}`;
  }

  private applyFilters(): void {
    const query = this.normalize(this.searchControl.value);
    const brand = this.brandControl.value;
    const filtered = this.rows
      .filter(row => !query || this.normalize(`${row.brand} ${row.model}`).includes(query))
      .filter(row => !brand || row.brand === brand)
      .filter(row => this.matchesStatus(row, this.statusControl.value))
      .filter(row => this.matchesAvailability(row, this.availabilityControl.value))
      .sort(this.comparator(this.sortControl.value));

    this.comparableRows = filtered.filter(row => this.isCashComparable(row));
    this.withoutComparisonRows = filtered.filter(row => !this.isCashComparable(row));
  }

  private matchesStatus(row: ComparativaProduct, status: FilterStatus): boolean {
    if (status === 'todos') {
      return true;
    }
    if (status === 'sin-comparativa') {
      return !this.isCashComparable(row);
    }
    if (!this.isCashComparable(row)) {
      return false;
    }
    return status === 'barato' ? row.differences.cashArs! > 0 : row.differences.cashArs! < 0;
  }

  private matchesAvailability(row: ComparativaProduct, availability: AvailabilityFilter): boolean {
    if (availability === 'todos') {
      return true;
    }
    return availability === 'comparables' ? this.isCashComparable(row) : !this.isCashComparable(row);
  }

  private comparator(sort: SortOption): (a: ComparativaProduct, b: ComparativaProduct) => number {
    return (a, b) => {
      if (sort === 'desventaja') {
        return this.compareOptional(a.differences.cashArs, b.differences.cashArs, 1) || this.compareModel(a, b);
      }
      if (sort === 'marca') {
        return a.brand.localeCompare(b.brand, 'es', { sensitivity: 'base' }) || this.compareModel(a, b);
      }
      if (sort === 'modelo') {
        return this.compareModel(a, b);
      }
      if (sort === 'precio-propio') {
        return this.compareOptional(a.mine.cashArs, b.mine.cashArs, 1) || this.compareModel(a, b);
      }
      if (sort === 'precio-competencia') {
        return this.compareOptional(a.competitor.cashArs, b.competitor.cashArs, 1) || this.compareModel(a, b);
      }
      return this.compareOptional(a.differences.cashArs, b.differences.cashArs, -1) || this.compareModel(a, b);
    };
  }

  private isCashComparable(row: ComparativaProduct): boolean {
    return row.matchStatus === 'MATCHED'
      && this.isPositiveNumber(row.mine.cashArs)
      && this.isPositiveNumber(row.competitor.cashArs)
      && this.isFiniteNumber(row.differences.cashArs);
  }

  private buildBrandOptions(rows: ComparativaProduct[]): SelectOption[] {
    const brands = Array.from(new Set(rows.map(row => row.brand).filter(Boolean)))
      .sort((a, b) => a.localeCompare(b, 'es', { sensitivity: 'base' }));
    return [{ label: 'Todas', value: '' }, ...brands.map(brand => ({ label: brand, value: brand }))];
  }

  private compareModel(a: ComparativaProduct, b: ComparativaProduct): number {
    return a.model.localeCompare(b.model, 'es', { sensitivity: 'base' });
  }

  private compareOptional(a: number | null | undefined, b: number | null | undefined, direction: 1 | -1): number {
    if (!this.isFiniteNumber(a)) {
      return this.isFiniteNumber(b) ? 1 : 0;
    }
    if (!this.isFiniteNumber(b)) {
      return -1;
    }
    return (a - b) * direction;
  }

  private catalogDate(): Date | null {
    return this.parseDate(this.catalogStatus?.last_successful_refresh_at ?? this.catalogStatus?.generated_at);
  }

  private parseDate(value: ApiDateValue | undefined): Date | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private normalize(value: string): string {
    return (value ?? '').toLowerCase().normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9\s]/g, ' ')
      .replace(/\s+/g, ' ').trim();
  }

  private arsFormatter(): Intl.NumberFormat {
    return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });
  }

  private isFiniteNumber(value: number | null | undefined): value is number {
    return typeof value === 'number' && Number.isFinite(value);
  }

  private isPositiveNumber(value: number | null | undefined): value is number {
    return this.isFiniteNumber(value) && value > 0;
  }
}
