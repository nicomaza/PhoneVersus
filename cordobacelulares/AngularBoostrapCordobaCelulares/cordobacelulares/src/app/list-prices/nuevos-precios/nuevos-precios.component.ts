import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, ViewChild, isDevMode } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, Subscription } from 'rxjs';
import {
  CatalogProductOrigin,
  NuevosPreciosService,
  TiendaPorteBrandResponse,
  TiendaPorteColorStockResponse,
  TiendaPorteModelResponse
} from '../../services/nuevos-precios.service';

type GrupoMarca = { marca: string; items: NuevoProductoLista[] };
type ColorStockLista = { color: string; stock: number | null };

export interface NuevoProductoLista {
  marca: string;
  modelo: string;
  origen?: CatalogProductOrigin | null;
  source?: CatalogProductOrigin | boolean | null;
  origin?: CatalogProductOrigin | boolean | null;
  provider?: CatalogProductOrigin | boolean | null;
  proveedor?: CatalogProductOrigin | boolean | null;
  fromSupplierSheet?: boolean | string | null;
  fromGoogleSheet?: boolean | string | null;
  efectivo?: number | null;
  transferencia?: number | null;
  tarjeta?: number | null;
  colores?: string[];
  coloresConStock?: ColorStockLista[];

  precioUsd?: number | null;
  precioPesos?: number | null;
  precioUsdt?: number | null;
  precioTransferenciaBancaria?: number | null;
  precioTarjeta3Pagos?: number | null;
  precioTarjeta6Pagos?: number | null;
  precioTarjeta12Pagos?: number | null;

  searchText: string;
  searchCompact: string;
  modelText: string;
  modelCompact: string;
}

@Component({
  selector: 'app-nuevos-precios',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './nuevos-precios.component.html',
  styleUrl: './nuevos-precios.component.css'
})
export class NuevosPreciosComponent implements OnDestroy {
  private readonly baseCategorias = [
    'IPHONE',
    'XIAOMI',
    'SAMSUNG',
    'MOTOROLA',
    'REALME',
    'INFINIX',
    'PRODUCTOS APPLE',
    'ARTICULOS VARIOS',
    'PERFUMES'
  ];
  private readonly extraCategoriaOrden = ['HUAWEI', 'HONOR', 'OPPO'];
  readonly SHEET_EMOJI = String.fromCodePoint(0x1F4F2);

  categorias = [...this.baseCategorias];

  catalogLoading = false;
  catalogLoaded = false;
  catalogErrorMessage: string | null = null;

  searchCtrl = new FormControl<string>('', { nonNullable: true });
  hasSearch = false;
  @ViewChild('searchInput') private searchInput?: ElementRef<HTMLInputElement>;

  grupos: GrupoMarca[] = [];
  expandedCategory: string | null = null;

  selected: NuevoProductoLista | null = null;
  modalOpen = false;
  warningModalOpen = false;
  pendingWhatsappProduct: NuevoProductoLista | null = null;
  warningTitle = '';
  warningMessage = '';

  private readonly WHATSAPP_PHONE = '5493512129922';
  private readonly CATEGORY_LIMIT = 50;

  private all: NuevoProductoLista[] = [];
  private allByKey = new Map<string, NuevoProductoLista>();
  private categoryProducts: Record<string, NuevoProductoLista[]> = {};
  private categoryLoaded: Record<string, boolean> = {};
  private readonly categoryErrors: Record<string, string | null> = {};
  private readonly categoryLoading: Record<string, boolean> = {};
  private readonly categoryLoadingMore: Record<string, boolean> = {};
  private readonly paginatedCategories = new Set<string>();
  private readonly subscriptions = new Subscription();
  private readonly scrollHandler = (): void => this.handleWindowScroll();

  constructor(private service: NuevosPreciosService) {
    window.addEventListener('scroll', this.scrollHandler, { passive: true });
    this.applyFilter('');
    this.loadCatalogInBackground();

    this.subscriptions.add(
      this.searchCtrl.valueChanges
        .pipe(debounceTime(120), distinctUntilChanged())
        .subscribe(q => this.applyFilter(q))
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    window.removeEventListener('scroll', this.scrollHandler);
    document.body.classList.remove('modal-open');
  }

  fmtMoney(n?: number | null): string {
    return this.hasValidMoney(n) ? n.toLocaleString('es-AR') : '-';
  }

  fmtPrice(n?: number | null): string {
    return this.hasValidMoney(n) ? `$ ${this.fmtMoney(n)}` : '-';
  }

  fmtPriceNoCents(n?: number | null): string {
    return this.hasValidMoney(n)
      ? `$ ${n.toLocaleString('es-AR', { maximumFractionDigits: 0 })}`
      : '-';
  }

  formatColorStock(c: ColorStockLista): string {
    return c.color;
  }

  colorNames(p: NuevoProductoLista): string[] {
    const fromStock = (p.coloresConStock ?? [])
      .map(c => this.cleanText(c.color))
      .filter(Boolean);

    if (fromStock.length > 0) {
      return fromStock;
    }

    return (p.colores ?? [])
      .map(c => this.cleanText(c))
      .filter(Boolean);
  }

  cuotaTarjeta6(p: NuevoProductoLista): number | null {
    return this.hasValidMoney(p.precioTarjeta6Pagos) ? p.precioTarjeta6Pagos / 6 : null;
  }

  hasCuotaTarjeta6(p: NuevoProductoLista): boolean {
    return this.cuotaTarjeta6(p) !== null;
  }

  isGoogleSheetProduct(p: NuevoProductoLista | null | undefined): boolean {
    const raw = String(
      p?.origen ??
      p?.source ??
      p?.origin ??
      p?.provider ??
      p?.proveedor ??
      ''
    ).trim().toUpperCase();
    const normalized = this.normalizeOriginSignal(raw);

    return raw === 'GOOGLE_SHEET'
      || normalized === 'GOOGLESHEET'
      || raw === 'SUPPLIER_SHEET'
      || normalized === 'SUPPLIERSHEET'
      || raw === 'SHEET'
      || normalized === 'SHEET';
  }

  getSheetEmoji(p: NuevoProductoLista | null | undefined): string {
    return this.isGoogleSheetProduct(p) ? this.SHEET_EMOJI : '';
  }

  openDetails(p: NuevoProductoLista): void {
    this.selected = p;
    this.modalOpen = true;
    document.body.classList.add('modal-open');
  }

  closeModal(): void {
    this.modalOpen = false;
    this.selected = null;
    if (!this.warningModalOpen) {
      document.body.classList.remove('modal-open');
    }
  }

  abrirWhatsApp(p: NuevoProductoLista): void {
    const warning = this.warningForProduct(p);
    if (warning) {
      this.openWarningModal(p, warning);
      return;
    }

    this.openWhatsApp(p);
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.applyFilter('');
    setTimeout(() => this.searchInput?.nativeElement?.focus?.());
  }

  cancelWarningModal(): void {
    this.closeWarningModal();
  }

  confirmWarningWhatsapp(): void {
    const product = this.pendingWhatsappProduct;
    this.closeWarningModal();

    if (product) {
      this.openWhatsApp(product);
    }
  }

  private openWhatsApp(p: NuevoProductoLista): void {
    const url = this.buildWhatsappUrl(p);

    if (isDevMode()) {
      const msg = this.buildWhatsappMessage(p);
      console.debug('[nuevosprecios][wa-msg]', msg);
      console.debug(
        '[nuevosprecios][wa-emoji]',
        this.SHEET_EMOJI,
        this.SHEET_EMOJI.codePointAt(0)?.toString(16)
      );
      console.debug('[nuevosprecios][wa-url]', url);
    }

    window.open(url, '_blank', 'noopener,noreferrer');
  }

  private buildWhatsappMessage(p: NuevoProductoLista): string {
    const colores = this.formatColorsForMessage(p);
    const cuota = this.cuotaTarjeta6(p);
    const sheetEmoji = this.isGoogleSheetProduct(p) ? ` ${this.SHEET_EMOJI}` : '';

    return [
      `Hola! Quiero consultar disponibilidad del *${p.marca} ${p.modelo}*.${sheetEmoji}`,
      `- Efectivo: $ ${this.fmtMoney(p.precioPesos)}`,
      `- Transferencia: $ ${this.fmtMoney(p.precioTransferenciaBancaria)}`,
      `- Tarjeta 6 pagos: $ ${this.fmtMoney(p.precioTarjeta6Pagos)}`,
      `- 6 cuotas de: $ ${this.fmtMoney(cuota)}`,
      `Colores: ${colores}`
    ].join('\n');
  }

  private buildWhatsappUrl(p: NuevoProductoLista): string {
    const phone = this.WHATSAPP_PHONE.replace(/[^\d]/g, '');
    const msg = this.buildWhatsappMessage(p);

    const encodedText = encodeURIComponent(msg);
    return `https://wa.me/${phone}?text=${encodedText}`;
  }

  private warningForProduct(p: NuevoProductoLista): { title: string; message: string } | null {
    const marca = this.normalizeCategory(p.marca);

    if (marca === 'articulosvarios') {
      return {
        title: 'Confirmar consulta',
        message: 'Este art\u00EDculo en particular requiere pago anticipado. \u00BFDesea continuar?'
      };
    }

    if (marca === 'perfumes') {
      return {
        title: 'Confirmar consulta',
        message: 'Los perfumes requieren pago completo anticipado. \u00BFDesea continuar?'
      };
    }

    return null;
  }

  private openWarningModal(
    product: NuevoProductoLista,
    warning: { title: string; message: string }
  ): void {
    this.pendingWhatsappProduct = product;
    this.warningTitle = warning.title;
    this.warningMessage = warning.message;
    this.warningModalOpen = true;
    document.body.classList.add('modal-open');
  }

  private closeWarningModal(): void {
    this.warningModalOpen = false;
    this.pendingWhatsappProduct = null;
    this.warningTitle = '';
    this.warningMessage = '';

    if (!this.modalOpen) {
      document.body.classList.remove('modal-open');
    }
  }

  toggleCategory(categoria: string): void {
    if (this.expandedCategory === categoria) {
      this.expandedCategory = null;
      return;
    }

    this.expandedCategory = categoria;
    this.categoryErrors[categoria] = null;

    if (this.catalogLoaded || this.service.hasCatalogCache()) {
      this.loadCategoryFromCatalog(categoria);
      return;
    }

    const cached = this.service.getCachedCategory(categoria);
    if (cached) {
      const products = this.mapResponse(cached);
      this.setCategoryProducts(categoria, products);
      this.mergeProductsIntoMemory(products);
      this.paginatedCategories.add(categoria);
      this.applyFilter(this.searchCtrl.value, false);
      return;
    }

    this.loadCategoryPage(categoria, 1, false);
  }

  categoryItems(categoria: string): NuevoProductoLista[] {
    return this.categoryProducts[categoria] ?? [];
  }

  isExpanded(categoria: string): boolean {
    return this.expandedCategory === categoria;
  }

  isCategoryLoading(categoria: string): boolean {
    return this.categoryLoading[categoria] === true;
  }

  isCategoryLoadingMore(categoria: string): boolean {
    return this.categoryLoadingMore[categoria] === true;
  }

  categoryError(categoria: string): string | null {
    return this.categoryErrors[categoria] ?? null;
  }

  categoryCountLabel(categoria: string): string {
    if (this.isCategoryLoading(categoria)) {
      return 'Cargando...';
    }

    const count = this.categoryItems(categoria).length;
    if (count > 0) {
      return `${count} modelos`;
    }
    if (this.categoryLoaded[categoria]) {
      return '0 modelos';
    }
    return 'Ver modelos';
  }

  private loadCatalogInBackground(): void {
    this.catalogLoading = true;
    this.catalogErrorMessage = null;

    this.subscriptions.add(
      this.service.getCatalogoPrincipal().subscribe({
        next: data => {
          const products = this.mapResponse(data);
          this.replaceMemory(products);
          this.refreshDynamicCategories();
          this.refreshCategoriesFromCatalog();
          this.catalogLoaded = true;
          this.catalogLoading = false;
          this.catalogErrorMessage = null;

          if (this.expandedCategory) {
            this.loadCategoryFromCatalog(this.expandedCategory);
          }

          this.applyFilter(this.searchCtrl.value, false);
        },
        error: () => {
          this.catalogLoading = false;
          this.catalogErrorMessage = 'No se pudo cargar el catalogo actualizado.';
          this.applyFilter(this.searchCtrl.value, false);
        }
      })
    );
  }

  private loadCategoryFromCatalog(categoria: string): void {
    const products = this.productsForCategory(this.all, categoria);
    this.setCategoryProducts(categoria, products);
    this.categoryLoading[categoria] = false;
    this.categoryLoadingMore[categoria] = false;
    this.categoryErrors[categoria] = null;
    this.paginatedCategories.delete(categoria);
  }

  private loadCategoryPage(categoria: string, page: number, append: boolean): void {
    if (this.catalogLoaded) {
      this.loadCategoryFromCatalog(categoria);
      return;
    }

    const state = this.service.getCategoryPageState(categoria);
    if (append && (!state?.hasNext || state.loading)) {
      return;
    }

    if (this.isCategoryLoading(categoria) || this.isCategoryLoadingMore(categoria)) {
      return;
    }

    if (append) {
      this.categoryLoadingMore[categoria] = true;
    } else {
      this.categoryLoading[categoria] = true;
    }
    this.categoryErrors[categoria] = null;

    const resetLoading = (): void => {
      this.categoryLoading[categoria] = false;
      this.categoryLoadingMore[categoria] = false;
    };

    this.subscriptions.add(
      this.service.getCategoryPage(categoria, page, this.CATEGORY_LIMIT).subscribe({
        next: response => {
          if (this.catalogLoaded) {
            this.loadCategoryFromCatalog(categoria);
            return;
          }
          const rawCategory = this.service.getCachedCategory(categoria) ?? response.data ?? [];
          const products = this.mapResponse(rawCategory);
          this.setCategoryProducts(categoria, products);
          this.mergeProductsIntoMemory(products);
          this.paginatedCategories.add(categoria);
          this.applyFilter(this.searchCtrl.value, false);
        },
        error: () => {
          if (this.catalogLoaded) {
            this.loadCategoryFromCatalog(categoria);
            resetLoading();
            return;
          }
          this.categoryErrors[categoria] = 'No se pudo cargar esta categoria.';
          resetLoading();
        },
        complete: resetLoading
      })
    );
  }

  private handleWindowScroll(): void {
    const categoria = this.expandedCategory;
    if (!categoria || this.hasSearch || this.catalogLoaded || !this.paginatedCategories.has(categoria)) {
      return;
    }

    const state = this.service.getCategoryPageState(categoria);
    if (!state?.hasNext || state.loading || this.isCategoryLoading(categoria) || this.isCategoryLoadingMore(categoria)) {
      return;
    }

    const doc = document.documentElement;
    const pageHeight = Math.max(doc.scrollHeight, document.body.scrollHeight);
    const scrollBottom = window.scrollY + window.innerHeight;

    if (scrollBottom >= pageHeight - 600) {
      this.loadCategoryPage(categoria, state.page + 1, true);
    }
  }

  private mapResponse(data: TiendaPorteBrandResponse[] | null): NuevoProductoLista[] {
    if (!Array.isArray(data)) {
      return [];
    }

    const productos: NuevoProductoLista[] = [];
    for (const brand of data) {
      const marca = this.cleanText(brand?.marca) || 'Sin marca';
      const modelos = Array.isArray(brand?.modelos) ? brand.modelos : [];

      for (const model of modelos) {
        const producto = this.mapModel(marca, model);
        if (producto) {
          productos.push(producto);
        }
      }
    }

    return this.dedupeProducts(productos);
  }

  private mapModel(marca: string, model: TiendaPorteModelResponse | null | undefined): NuevoProductoLista | null {
    const modelo = this.cleanText(model?.modeloNombre);
    if (!modelo) {
      return null;
    }

    const coloresConStock = this.mapColors(model?.colores);
    const baseSearch = `${marca} ${modelo}`;
    const searchText = this.normalizeQ(baseSearch);
    const searchCompact = searchText.replace(/\s/g, '');
    const modelText = this.normalizeQ(modelo);
    const modelCompact = modelText.replace(/\s/g, '');

    const precioPesos = this.safeMoney(model?.precioPesos);
    const precioTransferenciaBancaria = this.safeMoney(model?.precioTransferenciaBancaria);
    const precioTarjeta6Pagos = this.safeMoney(model?.precioTarjeta6Pagos);

    return {
      marca,
      modelo,
      origen: model?.origen ?? null,
      source: model?.source ?? null,
      origin: model?.origin ?? null,
      provider: model?.provider ?? null,
      proveedor: model?.proveedor ?? null,
      fromSupplierSheet: model?.fromSupplierSheet ?? null,
      fromGoogleSheet: model?.fromGoogleSheet ?? null,
      efectivo: precioPesos,
      transferencia: precioTransferenciaBancaria,
      tarjeta: precioTarjeta6Pagos,
      colores: coloresConStock.map(c => c.color),
      coloresConStock,
      precioUsd: this.safeMoney(model?.precioUsd),
      precioPesos,
      precioUsdt: this.safeMoney(model?.precioUsdt),
      precioTransferenciaBancaria,
      precioTarjeta3Pagos: this.safeMoney(model?.precioTarjeta3Pagos),
      precioTarjeta6Pagos,
      precioTarjeta12Pagos: this.safeMoney(model?.precioTarjeta12Pagos),
      searchText,
      searchCompact,
      modelText,
      modelCompact,
    };
  }

  private mapColors(colors: TiendaPorteColorStockResponse[] | null | undefined): ColorStockLista[] {
    if (!Array.isArray(colors)) {
      return [];
    }

    return colors
      .map(c => ({
        color: this.cleanText(c?.color),
        stock: this.safeStock(c?.stock),
      }))
      .filter((c): c is ColorStockLista => !!c.color);
  }

  private cleanText(value: string | null | undefined): string {
    return (value ?? '').trim();
  }

  private safeMoney(value: number | null | undefined): number | null {
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
  }

  private safeStock(value: number | null | undefined): number | null {
    return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null;
  }

  private formatColorsForMessage(p: NuevoProductoLista): string {
    const colors = this.colorNames(p);
    if (!colors.length) {
      return 'a confirmar';
    }
    return colors.join(', ');
  }

  private applyFilter(qRaw: string, scrollToTop = true): void {
    const qNorm = this.normalizeQ(qRaw ?? '');
    this.hasSearch = !!qNorm;

    if (!qNorm) {
      this.grupos = [];
      return;
    }

    const ranked = this.smartSearch(this.all, qRaw);
    this.grupos = this.groupByBrand(ranked);

    if (scrollToTop && window.scrollY > 120) {
      requestAnimationFrame(() => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    }
  }

  private replaceMemory(products: NuevoProductoLista[]): void {
    this.allByKey = new Map<string, NuevoProductoLista>();
    for (const product of products) {
      this.allByKey.set(this.productKey(product), product);
    }
    this.all = Array.from(this.allByKey.values());
  }

  private mergeProductsIntoMemory(products: NuevoProductoLista[]): void {
    let changed = false;
    for (const product of products) {
      const key = this.productKey(product);
      const existing = this.allByKey.get(key);
      if (!existing) {
        this.allByKey.set(key, product);
        changed = true;
        continue;
      }

      if (this.copyMissingOriginFields(existing, product)) {
        changed = true;
      }
    }

    if (changed) {
      this.all = Array.from(this.allByKey.values());
    }
  }

  private setCategoryProducts(categoria: string, products: NuevoProductoLista[]): void {
    this.categoryProducts = {
      ...this.categoryProducts,
      [categoria]: this.dedupeProducts(products)
    };
    this.categoryLoaded = {
      ...this.categoryLoaded,
      [categoria]: true
    };
  }

  private refreshCategoriesFromCatalog(): void {
    for (const categoria of this.categorias) {
      this.setCategoryProducts(categoria, this.productsForCategory(this.all, categoria));
    }
  }

  private refreshDynamicCategories(): void {
    const baseKeys = new Set(this.baseCategorias.map(categoria => this.normalizeCategory(categoria)));
    const extrasByKey = new Map<string, string>();

    for (const product of this.all) {
      const marca = this.cleanText(product.marca);
      const key = this.normalizeCategory(marca);
      if (!key || baseKeys.has(key) || extrasByKey.has(key)) {
        continue;
      }
      extrasByKey.set(key, marca);
    }

    const extras = Array.from(extrasByKey.values()).sort((a, b) => this.compareExtraCategories(a, b));
    this.categorias = [...this.baseCategorias, ...extras];
  }

  private compareExtraCategories(a: string, b: string): number {
    const aIndex = this.extraCategoryIndex(a);
    const bIndex = this.extraCategoryIndex(b);

    if (aIndex !== bIndex) {
      return aIndex - bIndex;
    }
    return a.localeCompare(b, 'es', { sensitivity: 'base' });
  }

  private extraCategoryIndex(categoria: string): number {
    const normalized = this.normalizeCategory(categoria);
    const index = this.extraCategoriaOrden.findIndex(item => this.normalizeCategory(item) === normalized);
    return index === -1 ? Number.MAX_SAFE_INTEGER : index;
  }

  private productsForCategory(products: NuevoProductoLista[], categoria: string): NuevoProductoLista[] {
    const target = this.normalizeQ(categoria);
    return products.filter(product => this.normalizeQ(product.marca) === target);
  }

  private dedupeProducts(products: NuevoProductoLista[]): NuevoProductoLista[] {
    const map = new Map<string, NuevoProductoLista>();
    for (const product of products) {
      const key = this.productKey(product);
      const existing = map.get(key);
      if (!existing) {
        map.set(key, product);
        continue;
      }

      this.copyMissingOriginFields(existing, product);
    }
    return Array.from(map.values());
  }

  private copyMissingOriginFields(target: NuevoProductoLista, source: NuevoProductoLista): boolean {
    let changed = false;

    if (target.origen == null && source.origen != null) {
      target.origen = source.origen;
      changed = true;
    }
    if (target.source == null && source.source != null) {
      target.source = source.source;
      changed = true;
    }
    if (target.origin == null && source.origin != null) {
      target.origin = source.origin;
      changed = true;
    }
    if (target.provider == null && source.provider != null) {
      target.provider = source.provider;
      changed = true;
    }
    if (target.proveedor == null && source.proveedor != null) {
      target.proveedor = source.proveedor;
      changed = true;
    }
    if (target.fromSupplierSheet == null && source.fromSupplierSheet != null) {
      target.fromSupplierSheet = source.fromSupplierSheet;
      changed = true;
    }
    if (target.fromGoogleSheet == null && source.fromGoogleSheet != null) {
      target.fromGoogleSheet = source.fromGoogleSheet;
      changed = true;
    }

    return changed;
  }

  private productKey(product: NuevoProductoLista): string {
    return `${this.normalizeQ(product.marca)}|${this.normalizeQ(product.modelo)}`;
  }

  private hasValidMoney(value: number | null | undefined): value is number {
    return typeof value === 'number' && Number.isFinite(value);
  }

  private groupByBrand(list: NuevoProductoLista[]): GrupoMarca[] {
    const map = new Map<string, NuevoProductoLista[]>();

    for (const p of list) {
      const key = p.marca || 'Sin marca';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(p);
    }

    return Array.from(map.entries()).map(([marca, items]) => ({
      marca,
      items,
    }));
  }

  private normalizeQ(txt: string): string {
    return (txt ?? '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9\s]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private normalizeCategory(txt: string | null | undefined): string {
    return this.normalizeQ(txt ?? '').replace(/\s/g, '');
  }

  private normalizeOriginSignal(value: unknown): string {
    return String(value ?? '')
      .toUpperCase()
      .replace(/[^A-Z0-9]/g, '');
  }

  private buildTokens(qNorm: string): string[] {
    const raw = qNorm.split(' ').filter(Boolean);
    const tokens: string[] = [];

    for (let i = 0; i < raw.length; i++) {
      const t = raw[i];
      const next = raw[i + 1];

      tokens.push(t);

      if (/^[a-z]$/.test(t) && /^\d{1,3}$/.test(next)) tokens.push(t + next);
      if (/^\d{1,3}$/.test(t) && /^[a-z]$/.test(next)) tokens.push(t + next);
    }

    const compact = qNorm.replace(/\s/g, '');
    if (compact && compact !== qNorm) tokens.push(compact);

    return Array.from(new Set(tokens));
  }

  private levenshtein(a: string, b: string): number {
    if (a === b) return 0;
    if (!a) return b.length;
    if (!b) return a.length;

    const m = a.length, n = b.length;
    const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));

    for (let i = 0; i <= m; i++) dp[i][0] = i;
    for (let j = 0; j <= n; j++) dp[0][j] = j;

    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        const cost = a[i - 1] === b[j - 1] ? 0 : 1;
        dp[i][j] = Math.min(
          dp[i - 1][j] + 1,
          dp[i][j - 1] + 1,
          dp[i - 1][j - 1] + cost
        );
      }
    }
    return dp[m][n];
  }

  private similarity(a: string, b: string): number {
    if (!a || !b) return 0;
    if (a === b) return 1;

    const la = a.length, lb = b.length;
    const short = la <= lb ? a : b;
    const long = la > lb ? a : b;

    if (long.includes(short) && short.length >= 3 && (short.length / long.length) >= 0.75) {
      return 0.95;
    }

    const dist = this.levenshtein(a, b);
    return 1 - dist / Math.max(la, lb);
  }

  private tokenMatchesProduct(token: string, p: NuevoProductoLista): boolean {
    const t = token;
    const tCompact = t.replace(/\s/g, '');

    if (p.modelCompact?.includes(tCompact) || p.searchCompact?.includes(tCompact)) return true;

    const isCompound = /[a-z]/.test(tCompact) && /\d/.test(tCompact) && tCompact.length >= 5;
    if (isCompound) {
      const s1 = this.similarity(tCompact, p.modelCompact ?? '');
      const s2 = this.similarity(tCompact, p.searchCompact ?? '');
      return Math.max(s1, s2) >= 0.82;
    }

    const haystack = `${p.modelText ?? ''} ${this.normalizeQ(p.marca ?? '')}`;
    const words = haystack.split(' ').filter(Boolean);

    let best = 0;
    for (const w of words) best = Math.max(best, this.similarity(t, w));
    return best >= 0.70;
  }

  private smartSearch(list: NuevoProductoLista[], qRaw: string): NuevoProductoLista[] {
    const qNorm = this.normalizeQ(qRaw);
    if (!qNorm) return list;

    const tokens = this.buildTokens(qNorm);
    const strong = tokens.filter(t => /[a-z]/.test(t) && t.length >= 2);
    const nums = tokens.filter(t => /^\d+$/.test(t));
    const qCompact = qNorm.replace(/\s/g, '');

    const scored: { p: NuevoProductoLista; score: number }[] = [];

    for (const p of list) {
      if (strong.length && !strong.every(t => this.tokenMatchesProduct(t, p))) continue;

      let score = 0;

      if (qCompact && p.searchCompact?.includes(qCompact)) score += 1000;
      else if (p.searchText?.includes(qNorm)) score += 700;

      for (const t of strong) {
        const tCompact = t.replace(/\s/g, '');
        if (p.modelCompact?.includes(tCompact)) score += 120;
        else if (p.searchCompact?.includes(tCompact)) score += 70;
        else {
          const words = (p.modelText ?? '').split(' ').filter(Boolean);
          let best = 0;
          for (const w of words) best = Math.max(best, this.similarity(t, w));
          score += best * 60;
        }
      }

      for (const t of nums) {
        if (p.modelText?.includes(t)) score += 15;
      }

      scored.push({ p, score });
    }

    scored.sort((a, b) => b.score - a.score);
    return scored.map(x => x.p);
  }
}
