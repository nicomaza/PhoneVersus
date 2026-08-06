import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subscription, debounceTime, distinctUntilChanged, merge } from 'rxjs';
import {
  ComparativaMetric,
  ComparativaProduct,
  ComparativaResponse
} from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

type FilterStatus = 'todos' | 'barato' | 'caro' | 'mixto' | 'sin-match';
type AvailabilityFilter = 'todos' | 'comparables' | 'incompletos';
type SortOption = 'ventaja' | 'desventaja' | 'marca' | 'modelo' | 'precio-propio' | 'precio-competencia';

interface SelectOption {
  label: string;
  value: string;
}

interface DetailLine {
  label: string;
  mine?: string;
  competitor?: string;
  result?: string;
  value?: string;
  status?: string;
}

interface CompactDifferenceLine {
  label: string;
  difference: number;
  status: string;
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
  readonly searchControl = new FormControl<string>('', { nonNullable: true });
  readonly brandControl = new FormControl<string>('', { nonNullable: true });
  readonly statusControl = new FormControl<FilterStatus>('todos', { nonNullable: true });
  readonly availabilityControl = new FormControl<AvailabilityFilter>('todos', { nonNullable: true });
  readonly sortControl = new FormControl<SortOption>('ventaja', { nonNullable: true });

  readonly statusOptions: SelectOption[] = [
    { label: 'Todos', value: 'todos' },
    { label: 'Yo mas barato', value: 'barato' },
    { label: 'Yo mas caro', value: 'caro' },
    { label: 'Resultados mixtos', value: 'mixto' },
    { label: 'Sin match', value: 'sin-match' }
  ];

  readonly availabilityOptions: SelectOption[] = [
    { label: 'Todas', value: 'todos' },
    { label: 'Con comparacion', value: 'comparables' },
    { label: 'Sin comparacion completa', value: 'incompletos' }
  ];

  readonly sortOptions: SelectOption[] = [
    { label: 'Mayor ventaja mia', value: 'ventaja' },
    { label: 'Mayor desventaja mia', value: 'desventaja' },
    { label: 'Marca', value: 'marca' },
    { label: 'Modelo', value: 'modelo' },
    { label: 'Precio propio', value: 'precio-propio' },
    { label: 'Precio LiberadosYa', value: 'precio-competencia' }
  ];

  response: ComparativaResponse | null = null;
  rows: ComparativaProduct[] = [];
  visibleRows: ComparativaProduct[] = [];
  brandOptions: SelectOption[] = [{ label: 'Todas', value: '' }];
  loading = false;
  error = '';

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
    this.changeDetectorRef.markForCheck();

    this.subscription.add(
      this.adminConfigService.getComparativa().subscribe({
        next: response => {
          this.response = response;
          this.rows = Array.isArray(response.products) ? response.products : [];
          this.brandOptions = this.buildBrandOptions(this.rows);
          this.applyFilters();
          this.loading = false;
          this.changeDetectorRef.markForCheck();
        },
        error: (error: Error) => {
          this.error = error.message || 'No se pudo cargar la comparativa.';
          this.response = null;
          this.rows = [];
          this.visibleRows = [];
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

  metricValue(metric: ComparativaMetric | null | undefined, type: 'percent' | 'money' | 'rate'): string {
    if (!metric || !this.isFiniteNumber(metric.average)) {
      return 'Sin datos';
    }
    if (type === 'percent') {
      return this.formatPercent(metric.average);
    }
    if (type === 'rate') {
      return this.formatRate(metric.average);
    }
    return this.formatMoney(metric.average);
  }

  metricSub(metric: ComparativaMetric | null | undefined, type: 'percent' | 'money' | 'rate'): string {
    const count = metric?.count ?? 0;
    if (count <= 0) {
      return 'Sin productos validos';
    }
    const parts = [`${count} productos`];
    if (this.isFiniteNumber(metric?.median)) {
      parts.push(`mediana ${this.metricValue({ ...metric, average: metric.median }, type)}`);
    }
    return parts.join(' · ');
  }

  hasMetricData(metric: ComparativaMetric | null | undefined): boolean {
    return (metric?.count ?? 0) > 0;
  }

  formatMoney(value: number | null | undefined): string {
    if (!this.isPositiveNumber(value)) {
      return 'No disponible';
    }
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 0
    }).format(value);
  }

  formatUsd(value: number | null | undefined): string {
    if (!this.isPositiveNumber(value)) {
      return 'No disponible';
    }
    const formatted = new Intl.NumberFormat('es-AR', {
      maximumFractionDigits: 2
    }).format(value);
    return `USD ${formatted}`;
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
      style: 'currency',
      currency: 'ARS',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value)} / USD`;
  }

  differenceLabel(value: number | null | undefined, currency: 'ARS' | 'USD'): string {
    if (!this.isFiniteNumber(value)) {
      return 'Sin comparacion';
    }
    if (value === 0) {
      return 'Igual';
    }
    const amount = currency === 'USD' ? this.formatUsd(Math.abs(value)) : this.formatMoney(Math.abs(value));
    return value > 0 ? `${amount} mas barato` : `${amount} mas caro`;
  }

  comparisonClass(status: string | null | undefined): string {
    return {
      CHEAPER: 'is-cheaper',
      MORE_EXPENSIVE: 'is-expensive',
      EQUAL: 'is-equal'
    }[status ?? ''] ?? 'is-unavailable';
  }

  differenceClass(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return 'is-unavailable';
    }
    if (value > 0) {
      return 'is-cheaper';
    }
    if (value < 0) {
      return 'is-expensive';
    }
    return 'is-equal';
  }

  overallLabel(status: string | null | undefined): string {
    return {
      CHEAPER_ALL: 'Mas barato en todas',
      MORE_EXPENSIVE_ALL: 'Mas caro en todas',
      MIXED: 'Resultados mixtos',
      COMPETITIVE: 'Competitivo',
      INCOMPLETE: 'Sin comparacion completa'
    }[status ?? ''] ?? 'Sin comparacion completa';
  }

  matchLabel(row: ComparativaProduct): string {
    if (row.matchStatus === 'MATCHED') {
      return row.competitor.name || 'Coincidencia encontrada';
    }
    return row.matchMessage || 'Sin coincidencia en LiberadosYa';
  }

  ownDetails(row: ComparativaProduct): DetailLine[] {
    return [
      { label: 'Efectivo', value: this.formatMoney(row.mine.cashArs) },
      { label: 'Transferencia', value: this.formatMoney(row.mine.transferArs) },
      { label: 'Tarjeta', value: this.formatMoney(row.mine.cardArs) },
      { label: 'Precio USD', value: this.formatUsd(row.mine.priceUsd) },
      { label: 'Costo USD', value: this.formatUsd(row.mine.costUsd) }
    ].filter(line => line.value !== 'No disponible');
  }

  competitorDetails(row: ComparativaProduct): DetailLine[] {
    return [
      { label: 'Efectivo ARS', value: this.formatMoney(row.competitor.cashArs) },
      { label: 'Transferencia ARS', value: this.formatMoney(row.competitor.transferArs) },
      { label: 'Tarjeta ARS', value: this.formatMoney(row.competitor.cardArs) },
      { label: 'Efectivo USD', value: this.formatUsd(row.competitor.cashUsd) },
      { label: 'Precio actual USD', value: this.formatUsd(row.competitor.currentUsd) },
      { label: 'Precio lista USD', value: this.formatUsd(row.competitor.listUsd) }
    ].filter(line => line.value !== 'No disponible');
  }

  differenceDetails(row: ComparativaProduct): DetailLine[] {
    return [
      this.comparisonLine('Efectivo', row.mine.cashArs, row.competitor.cashArs, row.differences.cashArs, row.comparison.cash, 'ARS'),
      this.comparisonLine('Transferencia', row.mine.transferArs, row.competitor.transferArs, row.differences.transferArs, row.comparison.transfer, 'ARS'),
      this.comparisonLine('Tarjeta', row.mine.cardArs, row.competitor.cardArs, row.differences.cardArs, row.comparison.card, 'ARS'),
      this.comparisonLine('USD', row.mine.priceUsd, row.competitor.mainUsd, row.differences.usd, row.comparison.usd, 'USD')
    ].filter((line): line is DetailLine => line !== null);
  }

  compactDifferenceDetails(row: ComparativaProduct): CompactDifferenceLine[] {
    return [
      this.compactDifferenceLine('Ef.', row.mine.cashArs, row.competitor.cashArs, row.differences.cashArs, row.comparison.cash),
      this.compactDifferenceLine('Tr.', row.mine.transferArs, row.competitor.transferArs, row.differences.transferArs, row.comparison.transfer),
      this.compactDifferenceLine('Tarj.', row.mine.cardArs, row.competitor.cardArs, row.differences.cardArs, row.comparison.card)
    ].filter((line): line is CompactDifferenceLine => line !== null);
  }

  trackByRow(_: number, row: ComparativaProduct): string {
    return `${row.brand}|${row.model}|${row.origin ?? ''}|${row.mine.priceUsd ?? ''}`;
  }

  private applyFilters(): void {
    const query = this.normalize(this.searchControl.value);
    const brand = this.brandControl.value;
    const status = this.statusControl.value;
    const availability = this.availabilityControl.value;

    this.visibleRows = this.rows
      .filter(row => !query || this.normalize(`${row.brand} ${row.model}`).includes(query))
      .filter(row => !brand || row.brand === brand)
      .filter(row => this.matchesStatus(row, status))
      .filter(row => this.matchesAvailability(row, availability))
      .sort(this.comparator(this.sortControl.value));
  }

  private matchesStatus(row: ComparativaProduct, status: FilterStatus): boolean {
    if (status === 'todos') {
      return true;
    }
    if (status === 'barato') {
      return row.comparison.overall === 'CHEAPER_ALL' || row.comparison.usd === 'CHEAPER';
    }
    if (status === 'caro') {
      return row.comparison.overall === 'MORE_EXPENSIVE_ALL' || row.comparison.usd === 'MORE_EXPENSIVE';
    }
    if (status === 'mixto') {
      return row.comparison.overall === 'MIXED';
    }
    return row.matchStatus !== 'MATCHED';
  }

  private matchesAvailability(row: ComparativaProduct, availability: AvailabilityFilter): boolean {
    if (availability === 'todos') {
      return true;
    }
    const comparable = row.matchStatus === 'MATCHED' && row.comparison.comparableCount > 0;
    return availability === 'comparables' ? comparable : !comparable || row.comparison.overall === 'INCOMPLETE';
  }

  private comparator(sort: SortOption): (a: ComparativaProduct, b: ComparativaProduct) => number {
    return (a, b) => {
      if (sort === 'desventaja') {
        return this.safeNumber(a.differences.usd) - this.safeNumber(b.differences.usd);
      }
      if (sort === 'marca') {
        return a.brand.localeCompare(b.brand, 'es', { sensitivity: 'base' }) || this.compareModel(a, b);
      }
      if (sort === 'modelo') {
        return this.compareModel(a, b);
      }
      if (sort === 'precio-propio') {
        return this.safeNumber(a.mine.priceUsd) - this.safeNumber(b.mine.priceUsd);
      }
      if (sort === 'precio-competencia') {
        return this.safeNumber(a.competitor.mainUsd) - this.safeNumber(b.competitor.mainUsd);
      }
      const priorityDelta = this.priority(a) - this.priority(b);
      return priorityDelta || this.safeNumber(b.differences.usd) - this.safeNumber(a.differences.usd) || this.compareModel(a, b);
    };
  }

  private priority(row: ComparativaProduct): number {
    if (row.comparison.overall === 'CHEAPER_ALL') {
      return 0;
    }
    if (row.comparison.usd === 'CHEAPER') {
      return 1;
    }
    if (row.comparison.overall === 'MIXED') {
      return 2;
    }
    if (row.comparison.overall === 'COMPETITIVE' || row.comparison.usd === 'EQUAL') {
      return 3;
    }
    if (row.comparison.usd === 'MORE_EXPENSIVE' || row.comparison.overall === 'MORE_EXPENSIVE_ALL') {
      return 4;
    }
    return 5;
  }

  private comparisonLine(
    label: string,
    mine: number | null,
    competitor: number | null,
    difference: number | null,
    status: string,
    currency: 'ARS' | 'USD'
  ): DetailLine | null {
    if (!this.hasComparablePrices(mine, competitor, difference, status)) {
      return null;
    }

    return {
      label,
      mine: currency === 'USD' ? this.formatUsd(mine) : this.formatMoney(mine),
      competitor: currency === 'USD' ? this.formatUsd(competitor) : this.formatMoney(competitor),
      result: this.differenceLabel(difference, currency),
      status
    };
  }

  private compactDifferenceLine(
    label: string,
    mine: number | null,
    competitor: number | null,
    difference: number | null,
    status: string
  ): CompactDifferenceLine | null {
    if (!this.hasComparablePrices(mine, competitor, difference, status)) {
      return null;
    }

    return { label, difference, status };
  }

  private buildBrandOptions(rows: ComparativaProduct[]): SelectOption[] {
    const brands = Array.from(new Set(rows.map(row => row.brand).filter(Boolean)))
      .sort((a, b) => a.localeCompare(b, 'es', { sensitivity: 'base' }));
    return [{ label: 'Todas', value: '' }, ...brands.map(brand => ({ label: brand, value: brand }))];
  }

  private compareModel(a: ComparativaProduct, b: ComparativaProduct): number {
    return a.model.localeCompare(b.model, 'es', { sensitivity: 'base' });
  }

  private normalize(value: string): string {
    return (value ?? '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9\s]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private safeNumber(value: number | null | undefined): number {
    return this.isFiniteNumber(value) ? value : Number.POSITIVE_INFINITY;
  }

  private isFiniteNumber(value: number | null | undefined): value is number {
    return typeof value === 'number' && Number.isFinite(value);
  }

  private isPositiveNumber(value: number | null | undefined): value is number {
    return this.isFiniteNumber(value) && value > 0;
  }

  private hasComparablePrices(
    mine: number | null | undefined,
    competitor: number | null | undefined,
    difference: number | null | undefined,
    status: string | null | undefined
  ): difference is number {
    return status !== 'NOT_AVAILABLE'
      && this.isPositiveNumber(mine)
      && this.isPositiveNumber(competitor)
      && this.isFiniteNumber(difference);
  }
}
