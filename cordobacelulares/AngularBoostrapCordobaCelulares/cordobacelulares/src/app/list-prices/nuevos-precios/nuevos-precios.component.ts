import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, ViewChild, isDevMode } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, Subscription } from 'rxjs';
import {
  CatalogProductOrigin,
  LiberadosYaDynamicRecord,
  LiberadosYaDynamicValue,
  LiberadosYaProductMatchResponse,
  NuevosPreciosService,
  TiendaPorteBrandResponse,
  TiendaPorteColorStockResponse,
  TiendaPorteModelResponse
} from '../../services/nuevos-precios.service';

type GrupoMarca = { marca: string; items: NuevoProductoLista[] };
type ColorStockLista = { color: string; stock: number | null };
type FeatureErrorKind = 'not-found' | 'ambiguous' | 'unavailable' | 'general';
type FeatureDisplayEntry = { label: string; value: string };
type FeatureDisplaySection = { label: string; entries: FeatureDisplayEntry[] };

export interface NuevoProductoLista {
  marca: string;
  modelo: string;
  showColorOnCard?: boolean;
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
    'SONY',
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
  @ViewChild('featuresCloseButton') private featuresCloseButton?: ElementRef<HTMLButtonElement>;

  grupos: GrupoMarca[] = [];
  expandedCategory: string | null = null;

  selected: NuevoProductoLista | null = null;
  modalOpen = false;
  featuresModalOpen = false;
  featuresLoading = false;
  featuresError: FeatureErrorKind | null = null;
  featuresProduct: NuevoProductoLista | null = null;
  featuresData: LiberadosYaProductMatchResponse | null = null;
  featuresImages: string[] = [];
  featuresImageIndex = 0;
  featuresSpecSections: FeatureDisplaySection[] = [];
  hasAvailableCharacteristics = false;
  characteristicsLoaded = false;
  matchedCharacteristicsProduct: LiberadosYaProductMatchResponse | null = null;
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
  private readonly failedFeatureImages = new Set<string>();
  private featuresRequestId = 0;
  private characteristicsRequestId = 0;
  private matchedCharacteristicsImages: string[] = [];
  private matchedCharacteristicsSpecSections: FeatureDisplaySection[] = [];
  private featuresTriggerElement: HTMLElement | null = null;
  private readonly specSectionLabels: Record<string, string> = {
    'FICHA TECNICA': 'General',
    GENERAL: 'General',
    PANTALLA: 'Pantalla',
    DISPLAY: 'Pantalla',
    PLATAFORMA: 'Plataforma',
    MEMORIA: 'Memoria',
    CAMARA: 'C\u00E1mara principal',
    CAMARA_PRINCIPAL: 'C\u00E1mara principal',
    'CAMARA PRINCIPAL': 'C\u00E1mara principal',
    CAMARA_FRONTAL: 'C\u00E1mara frontal',
    'CAMARA FRONTAL': 'C\u00E1mara frontal',
    CONECTIVIDAD: 'Conectividad',
    REDES: 'Redes',
    SONIDO: 'Sonido',
    SENSORES: 'Sensores',
    BATERIA: 'Bater\u00EDa',
    CUERPO: 'Dise\u00F1o y cuerpo',
    TAMANO: 'Dise\u00F1o y cuerpo',
    'DISENO Y CUERPO': 'Dise\u00F1o y cuerpo',
    LANZAMIENTO: 'Lanzamiento',
    CARACTERISTICAS: 'Caracter\u00EDsticas',
  };
  private readonly specSectionOrder: Record<string, number> = {
    GENERAL: 0,
    PANTALLA: 1,
    PLATAFORMA: 2,
    MEMORIA: 3,
    'CAMARA PRINCIPAL': 4,
    'CAMARA FRONTAL': 5,
    CONECTIVIDAD: 6,
    REDES: 7,
    SONIDO: 8,
    SENSORES: 9,
    BATERIA: 10,
    'DISENO Y CUERPO': 11,
    LANZAMIENTO: 12,
  };

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

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    if (this.featuresModalOpen) {
      event.preventDefault();
      event.stopPropagation();
      this.closeFeaturesModal();
      return;
    }

    if (this.warningModalOpen) {
      event.preventDefault();
      event.stopPropagation();
      this.cancelWarningModal();
      return;
    }

    if (this.modalOpen) {
      event.preventDefault();
      event.stopPropagation();
      this.closeModal();
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    window.removeEventListener('scroll', this.scrollHandler);
    document.body.classList.remove('modal-open');
  }

  fmtMoney(n?: number | null): string {
    return this.hasValidMoney(n)
      ? n.toLocaleString('es-AR', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
      })
      : '-';
  }

  fmtPrice(n?: number | null): string {
    return this.hasValidMoney(n) ? `$ ${this.fmtMoney(n)}` : '-';
  }

  fmtPriceNoCents(n?: number | null): string {
    return this.fmtPrice(n);
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

  cardColor(p: NuevoProductoLista): string {
    return p.showColorOnCard ? (this.colorNames(p)[0] ?? '') : '';
  }

  readonly trackByBrand = (_index: number, group: GrupoMarca): string => this.normalizeQ(group.marca);

  readonly trackByCategory = (_index: number, category: string): string => this.normalizeQ(category);

  readonly trackByProduct = (_index: number, product: NuevoProductoLista): string => this.productKey(product);

  cuotaTarjeta6(p: NuevoProductoLista): number | null {
    return this.hasValidMoney(p.precioTarjeta6Pagos) ? p.precioTarjeta6Pagos / 6 : null;
  }

  hasCuotaTarjeta6(p: NuevoProductoLista): boolean {
    return this.cuotaTarjeta6(p) !== null;
  }

  isPerfume(p: NuevoProductoLista | null | undefined): boolean {
    return this.normalizeCategory(p?.marca) === 'perfumes';
  }

  isArticulosVarios(p: NuevoProductoLista | null | undefined): boolean {
    return this.normalizeCategory(p?.marca) === 'articulosvarios';
  }

  shouldShowColors(p: NuevoProductoLista | null | undefined): boolean {
    return !!p && !this.isPerfume(p) && this.colorNames(p).length > 0;
  }

  modalSubtitle(p: NuevoProductoLista | null | undefined): string {
    return this.shouldShowColors(p) ? 'Precios y colores' : 'Precios';
  }

  cardHint(p: NuevoProductoLista | null | undefined): string {
    return this.shouldShowColors(p)
      ? 'Tocar para ver transferencia, tarjeta y colores'
      : 'Tocar para ver transferencia y tarjeta';
  }

  productConditionLabel(p: NuevoProductoLista | null | undefined): string {
    if (this.isPerfume(p)) {
      return 'En caja sellada de f\u00E1brica, sin abrir.';
    }

    if (this.isArticulosVarios(p)) {
      return 'Dispositivos nuevos, sellados de f\u00E1brica y con garant\u00EDa.';
    }

    return 'Equipos nuevos, sellados de f\u00E1brica y con garant\u00EDa.';
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
    this.resetCharacteristicsState();
    this.selected = p;
    this.modalOpen = true;
    document.body.classList.add('modal-open');
    this.preloadCharacteristics(p);
  }

  closeModal(): void {
    this.closeFeaturesModal(false);
    this.resetCharacteristicsState();
    this.modalOpen = false;
    this.selected = null;
    if (!this.warningModalOpen) {
      document.body.classList.remove('modal-open');
    }
  }

  openFeaturesModal(p: NuevoProductoLista, event?: Event): void {
    if (this.featuresModalOpen || !this.canShowCharacteristicsButton(p)) {
      return;
    }

    this.featuresTriggerElement = event?.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : null;
    this.featuresProduct = p;
    this.featuresLoading = false;
    this.featuresError = null;
    this.featuresData = this.matchedCharacteristicsProduct;
    this.featuresImages = [...this.matchedCharacteristicsImages];
    this.featuresImageIndex = 0;
    this.featuresSpecSections = this.cloneFeatureSections(this.matchedCharacteristicsSpecSections);
    this.failedFeatureImages.clear();
    this.featuresModalOpen = true;
    document.body.classList.add('modal-open');
    requestAnimationFrame(() => this.featuresCloseButton?.nativeElement.focus());
  }

  closeFeaturesModal(restoreFocus = true): void {
    if (!this.featuresModalOpen && !this.featuresProduct && !this.featuresLoading) {
      return;
    }

    this.featuresRequestId++;
    this.featuresModalOpen = false;
    this.featuresLoading = false;
    this.featuresError = null;
    this.featuresProduct = null;
    this.featuresData = null;
    this.featuresImages = [];
    this.featuresImageIndex = 0;
    this.featuresSpecSections = [];
    this.failedFeatureImages.clear();

    const trigger = this.featuresTriggerElement;
    this.featuresTriggerElement = null;

    if (!this.modalOpen && !this.warningModalOpen) {
      document.body.classList.remove('modal-open');
    }

    if (restoreFocus && trigger && document.contains(trigger)) {
      setTimeout(() => trigger.focus());
    }
  }

  retryFeatures(): void {
    if (!this.featuresProduct || !this.matchedCharacteristicsProduct) {
      return;
    }

    this.featuresData = this.matchedCharacteristicsProduct;
    this.featuresImages = [...this.matchedCharacteristicsImages];
    this.featuresSpecSections = this.cloneFeatureSections(this.matchedCharacteristicsSpecSections);
    this.featuresImageIndex = 0;
    this.featuresError = null;
  }

  isFeaturesButtonLoading(p: NuevoProductoLista): boolean {
    return this.featuresLoading && this.featuresProduct === p;
  }

  canShowCharacteristicsButton(p: NuevoProductoLista): boolean {
    return this.selected === p
      && this.hasAvailableCharacteristics
      && this.matchedCharacteristicsProduct !== null;
  }

  featureCurrentImage(): string | null {
    return this.featuresImages[this.featuresImageIndex] ?? null;
  }

  hasFeatureGalleryControls(): boolean {
    return this.featuresImages.length > 1;
  }

  selectFeatureImage(index: number): void {
    if (index < 0 || index >= this.featuresImages.length) {
      return;
    }

    this.featuresImageIndex = index;
  }

  previousFeatureImage(): void {
    if (!this.hasFeatureGalleryControls()) {
      return;
    }

    this.featuresImageIndex = this.featuresImageIndex === 0
      ? this.featuresImages.length - 1
      : this.featuresImageIndex - 1;
  }

  nextFeatureImage(): void {
    if (!this.hasFeatureGalleryControls()) {
      return;
    }

    this.featuresImageIndex = (this.featuresImageIndex + 1) % this.featuresImages.length;
  }

  onFeatureImageError(imageUrl: string): void {
    this.failedFeatureImages.add(imageUrl);
    const failedIndex = this.featuresImages.indexOf(imageUrl);
    this.featuresImages = this.featuresImages.filter(image => image !== imageUrl);

    if (this.featuresImages.length === 0) {
      this.featuresImageIndex = 0;
      return;
    }

    if (failedIndex >= 0 && failedIndex <= this.featuresImageIndex) {
      this.featuresImageIndex = Math.max(0, this.featuresImageIndex - 1);
    }
    if (this.featuresImageIndex >= this.featuresImages.length) {
      this.featuresImageIndex = this.featuresImages.length - 1;
    }
  }

  isFeatureImageFailed(imageUrl: string): boolean {
    return this.failedFeatureImages.has(imageUrl);
  }

  featureImageAlt(): string {
    const product = this.featuresProduct ?? this.selected;
    const name = `${product?.marca ?? ''} ${product?.modelo ?? ''}`.trim();
    return name ? `Imagen de ${name}` : 'Imagen del producto';
  }

  hasFeaturesContent(): boolean {
    return this.featuresImages.length > 0
      || this.featuresSpecSections.length > 0;
  }

  featuresErrorTitle(): string {
    switch (this.featuresError) {
      case 'not-found':
        return 'Caracter\u00EDsticas no disponibles';
      case 'ambiguous':
        return 'No pudimos elegir una ficha';
      case 'unavailable':
        return 'Cat\u00E1logo no disponible';
      case 'general':
        return 'No se pudo cargar la ficha';
      default:
        return '';
    }
  }

  featuresErrorMessage(): string {
    switch (this.featuresError) {
      case 'not-found':
        return 'No encontramos caracter\u00EDsticas para este modelo.';
      case 'ambiguous':
        return 'Hay m\u00E1s de una coincidencia posible para este producto.';
      case 'unavailable':
        return 'El cat\u00E1logo t\u00E9cnico no est\u00E1 disponible en este momento.';
      case 'general':
        return 'Intent\u00E1 nuevamente en unos segundos.';
      default:
        return '';
    }
  }

  private preloadCharacteristics(p: NuevoProductoLista): void {
    const matchParams = this.featureMatchParams(p);
    if (!matchParams) {
      this.characteristicsLoaded = true;
      return;
    }

    const requestId = ++this.characteristicsRequestId;
    const selectedKey = this.productKey(p);

    this.subscriptions.add(
      this.service.getLiberadosYaProductMatch(matchParams.brand, matchParams.model).subscribe({
        next: response => {
          if (!this.isActiveCharacteristicsRequest(requestId, selectedKey)) {
            return;
          }

          const images = this.buildFeatureImages(response);
          const specSections = this.buildSpecSections(response.especificaciones);

          this.matchedCharacteristicsProduct = response;
          this.matchedCharacteristicsImages = images;
          this.matchedCharacteristicsSpecSections = specSections;
          this.hasAvailableCharacteristics = images.length > 0 || specSections.length > 0;
        },
        error: () => {
          if (!this.isActiveCharacteristicsRequest(requestId, selectedKey)) {
            return;
          }

          this.characteristicsLoaded = true;
          this.hasAvailableCharacteristics = false;
          this.matchedCharacteristicsProduct = null;
          this.matchedCharacteristicsImages = [];
          this.matchedCharacteristicsSpecSections = [];
        },
        complete: () => {
          if (this.isActiveCharacteristicsRequest(requestId, selectedKey)) {
            this.characteristicsLoaded = true;
          }
        }
      })
    );
  }

  private featureMatchParams(p: NuevoProductoLista): { brand: string; model: string } | null {
    const brand = this.cleanText(p.marca);
    const model = this.cleanText(p.modelo);

    if (!brand || !model) {
      return null;
    }

    return { brand, model };
  }

  private buildFeatureImages(product: LiberadosYaProductMatchResponse): string[] {
    return this.sanitizeProductImages([
      product.imagen_principal,
      ...(Array.isArray(product.imagenes) ? product.imagenes : [])
    ]);
  }

  private resetCharacteristicsState(): void {
    this.characteristicsRequestId++;
    this.characteristicsLoaded = false;
    this.hasAvailableCharacteristics = false;
    this.matchedCharacteristicsProduct = null;
    this.matchedCharacteristicsImages = [];
    this.matchedCharacteristicsSpecSections = [];
  }

  private isActiveCharacteristicsRequest(requestId: number, productKey: string): boolean {
    return requestId === this.characteristicsRequestId
      && this.modalOpen
      && this.selected !== null
      && this.productKey(this.selected) === productKey;
  }

  private cloneFeatureSections(sections: FeatureDisplaySection[]): FeatureDisplaySection[] {
    return sections.map(section => ({
      label: section.label,
      entries: section.entries.map(entry => ({ ...entry }))
    }));
  }

  private sanitizeProductImages(candidates: unknown[]): string[] {
    const imagesByKey = new Map<string, { url: string; quality: number }>();
    for (const candidate of candidates) {
      const url = this.normalizedProductImageUrl(candidate);
      if (!url || !this.isSafeProductImageUrl(url)) {
        continue;
      }

      const key = this.imageDedupeKey(url);
      if (!key) {
        continue;
      }

      const quality = this.imageQuality(url);
      const existing = imagesByKey.get(key);
      if (!existing || quality > existing.quality) {
        imagesByKey.set(key, { url, quality });
      }
    }

    return Array.from(imagesByKey.values()).map(image => image.url);
  }

  private normalizedProductImageUrl(value: unknown): string {
    let raw = '';
    if (typeof value === 'string') {
      raw = value;
    } else if (value && typeof value === 'object') {
      const record = value as Record<string, unknown>;
      raw = typeof record['url'] === 'string' ? record['url'] : '';
    }

    raw = raw.trim();
    if (!raw) {
      return '';
    }

    try {
      const parsed = new URL(raw, 'https://www.cordobacelulares.com');
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        return '';
      }

      parsed.protocol = 'https:';
      parsed.hash = '';
      for (const param of ['w', 'width', 'h', 'height', 'size', 'resize', 'fit', 'crop', 'q', 'quality']) {
        parsed.searchParams.delete(param);
      }
      return parsed.toString();
    } catch {
      return '';
    }
  }

  private isSafeProductImageUrl(url: string): boolean {
    const normalized = url.toLowerCase();
    const blockedTerms = [
      'pinterest',
      'pinext',
      'pinimg',
      'facebook',
      'instagram',
      'whatsapp',
      'twitter',
      'tiktok',
      'youtube',
      'telegram',
      'linkedin',
      'logo',
      'logos',
      'favicon',
      'icon',
      'icons',
      'sprite',
      'flag',
      'flags',
      'bandera',
      'argentina',
      'usa',
      'united-states',
      'currency',
      'country',
      'locale',
      'language',
      'payment',
      'mercadopago',
      'visa',
      'mastercard',
      'social',
      'banner',
      'placeholder',
      'loading',
      'spinner',
      'no-image',
      'sin-imagen',
      'avatar',
      'profile',
      'shipping',
      'trust',
      'badge'
    ];

    if (blockedTerms.some(term => normalized.includes(term))) {
      return false;
    }

    try {
      const parsed = new URL(url);
      const host = parsed.hostname.toLowerCase();
      const path = parsed.pathname.toLowerCase();
      const allowedHost = host === 'acdn-us.mitiendanube.com'
        || host.endsWith('.mitiendanube.com')
        || host.endsWith('.nuvemshop.com');
      const productPath = /^\/(?:tmp\/)?stores\/[^/]+\/[^/]+\/[^/]+\/products\/.+/.test(path);

      return allowedHost && productPath && !path.includes('/themes/');
    } catch {
      return false;
    }
  }

  private imageDedupeKey(url: string): string {
    try {
      const parsed = new URL(url, 'https://www.cordobacelulares.com');
      parsed.hash = '';

      const path = parsed.pathname
        .toLowerCase()
        .replace(/[-_]\d{2,4}x\d{2,4}(?=\.[a-z0-9]+$)/g, '')
        .replace(/-\d{2,4}-\d{1,4}(?=\.[a-z0-9]+$)/g, '')
        .replace(/\/+/g, '/');

      const paramsToRemove = ['w', 'width', 'h', 'height', 'size', 'resize', 'fit', 'crop', 'q', 'quality'];
      for (const param of paramsToRemove) {
        parsed.searchParams.delete(param);
      }

      const queryPairs: Array<[string, string]> = [];
      parsed.searchParams.forEach((value, key) => queryPairs.push([key, value]));

      const search = queryPairs
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([key, value]) => `${key}=${value}`)
        .join('&');

      return `${parsed.hostname}${path}${search ? `?${search}` : ''}`;
    } catch {
      return url
        .toLowerCase()
        .replace(/[-_]\d{2,4}x\d{2,4}(?=\.[a-z0-9]+$)/g, '')
        .replace(/-\d{2,4}-\d{1,4}(?=\.[a-z0-9]+$)/g, '');
    }
  }

  private imageQuality(url: string): number {
    const matchSizePair = url.match(/-(\d{2,4})-(\d{1,4})(?=\.[a-z0-9]+(?:\?|$))/i);
    const matchSizeX = url.match(/[-_](\d{2,4})x(\d{2,4})(?=\.[a-z0-9]+(?:\?|$))/i);
    const values = [
      matchSizePair?.[1],
      matchSizePair?.[2],
      matchSizeX?.[1],
      matchSizeX?.[2]
    ]
      .map(value => Number(value ?? 0))
      .filter(value => Number.isFinite(value));

    return values.length > 0 ? Math.max(...values) : 0;
  }

  private buildSpecSections(
    specs: Record<string, LiberadosYaDynamicRecord | null | undefined> | null | undefined
  ): FeatureDisplaySection[] {
    if (!specs || typeof specs !== 'object') {
      return [];
    }

    const sections: Array<FeatureDisplaySection & { originalIndex: number }> = [];

    Object.entries(specs).forEach(([key, value], originalIndex) => {
      const entries = this.buildDynamicEntries(value);
      if (entries.length === 0) {
        return;
      }

      const sectionKey = this.normalizedDynamicKey(key);
      if (sectionKey === 'CAMARA') {
        const frontEntries = entries.filter(entry => this.isCameraFrontEntry(entry));
        const mainEntries = entries.filter(entry => !this.isCameraFrontEntry(entry));

        this.addSpecSection(sections, 'C\u00E1mara principal', mainEntries, originalIndex);
        this.addSpecSection(sections, 'C\u00E1mara frontal', frontEntries, originalIndex);
        return;
      }

      if (sectionKey === 'CARACTERISTICAS') {
        const sensorEntries = entries.filter(entry => this.normalizedDynamicKey(entry.label) === 'SENSORES');
        const remainingEntries = entries.filter(entry => this.normalizedDynamicKey(entry.label) !== 'SENSORES');

        this.addSpecSection(sections, 'Sensores', sensorEntries, originalIndex);
        this.addSpecSection(sections, 'Caracter\u00EDsticas', remainingEntries, originalIndex);
        return;
      }

      this.addSpecSection(sections, this.specSectionLabel(key), entries, originalIndex);
    });

    return this.mergeAndSortSpecSections(sections);
  }

  private buildDynamicEntries(record: LiberadosYaDynamicRecord | null | undefined): FeatureDisplayEntry[] {
    if (!record || typeof record !== 'object' || Array.isArray(record)) {
      return [];
    }

    const entries = Object.entries(record)
      .map(([key, value]) => ({
        label: this.prettyDynamicLabel(key),
        value: this.dynamicValueToText(value)
      }))
      .filter(entry => !!entry.label && !!entry.value);

    return this.dedupeFeatureEntries(entries);
  }

  private dynamicValueToText(value: LiberadosYaDynamicValue | undefined): string {
    if (this.isEmptyDynamicValue(value)) {
      return '';
    }

    if (Array.isArray(value)) {
      return this.joinUniqueTextParts(value
        .map(item => this.dynamicValueToText(item))
        .filter(Boolean), ', ');
    }

    if (typeof value === 'object' && value !== null) {
      return this.joinUniqueTextParts(Object.entries(value)
        .map(([key, innerValue]) => {
          const text = this.dynamicValueToText(innerValue);
          return text ? `${this.prettyDynamicLabel(key)}: ${text}` : '';
        })
        .filter(Boolean), '; ');
    }

    if (typeof value === 'boolean') {
      return value ? 'S\u00ED' : 'No';
    }

    if (typeof value === 'number') {
      return Number.isFinite(value)
        ? value.toLocaleString('es-AR', { maximumFractionDigits: 2 })
        : '';
    }

    return this.cleanText(value).replace(/\s+/g, ' ');
  }

  private isEmptyDynamicValue(value: LiberadosYaDynamicValue | undefined): boolean {
    if (value === null || value === undefined) {
      return true;
    }

    if (typeof value === 'string') {
      return !this.cleanText(value);
    }

    if (typeof value === 'number') {
      return !Number.isFinite(value);
    }

    if (typeof value === 'boolean') {
      return false;
    }

    if (Array.isArray(value)) {
      return value.length === 0 || value.every(item => this.isEmptyDynamicValue(item));
    }

    const entries = Object.entries(value);
    return entries.length === 0 || entries.every(([, innerValue]) => this.isEmptyDynamicValue(innerValue));
  }

  private specSectionLabel(key: string): string {
    const normalized = this.normalizedDynamicKey(key);
    return this.specSectionLabels[normalized] ?? this.prettyDynamicLabel(key);
  }

  private prettyDynamicLabel(key: string): string {
    const normalized = this.normalizedDynamicKey(key);
    const labelOverrides: Record<string, string> = {
      '2G': '2G',
      '3G': '3G',
      '4G': '4G',
      '5G': '5G',
      OS: 'OS',
      UI: 'UI',
      GPU: 'GPU',
      GPS: 'GPS',
      USB: 'USB',
      NFC: 'NFC',
      SIM: 'SIM',
      RAM: 'RAM',
      HDR: 'HDR',
      'WI FI': 'Wi-Fi',
      'RADIO FM': 'Radio FM',
      'MEMORIA RAM': 'Memoria RAM',
      'CAMARA PRINCIPAL': 'C\u00E1mara principal',
      'CAMARA FRONTAL': 'C\u00E1mara frontal',
    };

    if (labelOverrides[normalized]) {
      return labelOverrides[normalized];
    }

    const clean = this.cleanText(key)
      .replace(/_+/g, ' ')
      .replace(/\s+/g, ' ')
      .toLowerCase();

    if (!clean) {
      return '';
    }

    return clean
      .split(' ')
      .map((word, index) => this.prettyDynamicWord(word, index))
      .join(' ');
  }

  private prettyDynamicWord(word: string, index: number): string {
    const wordOverrides: Record<string, string> = {
      '2G': '2G',
      '3G': '3G',
      '4G': '4G',
      '5G': '5G',
      OS: 'OS',
      UI: 'UI',
      GPU: 'GPU',
      GPS: 'GPS',
      USB: 'USB',
      NFC: 'NFC',
      SIM: 'SIM',
      RAM: 'RAM',
      HDR: 'HDR',
      FM: 'FM',
      'WI FI': 'Wi-Fi',
    };
    const normalized = this.normalizedDynamicKey(word);

    if (wordOverrides[normalized]) {
      return wordOverrides[normalized];
    }

    if (!word) {
      return '';
    }

    return index === 0 ? `${word[0].toUpperCase()}${word.slice(1)}` : word;
  }

  private addSpecSection(
    sections: Array<FeatureDisplaySection & { originalIndex: number }>,
    label: string,
    entries: FeatureDisplayEntry[],
    originalIndex: number
  ): void {
    const cleanLabel = this.cleanText(label).replace(/\s+/g, ' ');
    const cleanEntries = this.dedupeFeatureEntries(entries);

    if (!cleanLabel || cleanEntries.length === 0) {
      return;
    }

    sections.push({
      label: cleanLabel,
      entries: cleanEntries,
      originalIndex
    });
  }

  private mergeAndSortSpecSections(
    sections: Array<FeatureDisplaySection & { originalIndex: number }>
  ): FeatureDisplaySection[] {
    const merged = new Map<string, FeatureDisplaySection & { originalIndex: number }>();

    for (const section of sections) {
      const label = this.specSectionLabel(section.label);
      const key = this.normalizedDynamicKey(label);
      const existing = merged.get(key);

      if (existing) {
        existing.entries = this.dedupeFeatureEntries([...existing.entries, ...section.entries]);
        existing.originalIndex = Math.min(existing.originalIndex, section.originalIndex);
        continue;
      }

      merged.set(key, {
        label,
        entries: this.dedupeFeatureEntries(section.entries),
        originalIndex: section.originalIndex
      });
    }

    return Array.from(merged.values())
      .sort((a, b) => {
        const orderDelta = this.specSectionSortValue(a.label) - this.specSectionSortValue(b.label);
        if (orderDelta !== 0) {
          return orderDelta;
        }

        return a.originalIndex - b.originalIndex
          || a.label.localeCompare(b.label, 'es', { sensitivity: 'base' });
      })
      .map(({ label, entries }) => ({ label, entries }));
  }

  private dedupeFeatureEntries(entries: FeatureDisplayEntry[]): FeatureDisplayEntry[] {
    const result: FeatureDisplayEntry[] = [];
    const indexByLabel = new Map<string, number>();
    const seenPairs = new Set<string>();

    for (const entry of entries) {
      const label = this.cleanText(entry.label).replace(/\s+/g, ' ');
      const value = this.cleanText(entry.value).replace(/\s+/g, ' ');
      const labelKey = this.normalizedDynamicKey(label);
      const valueKey = this.normalizeFeatureValue(value);

      if (!label || !value || !labelKey || !valueKey) {
        continue;
      }

      const pairKey = `${labelKey}|${valueKey}`;
      if (seenPairs.has(pairKey)) {
        continue;
      }
      seenPairs.add(pairKey);

      const existingIndex = indexByLabel.get(labelKey);
      if (existingIndex !== undefined) {
        result[existingIndex] = {
          ...result[existingIndex],
          value: this.joinUniqueTextParts([result[existingIndex].value, value], ' / ')
        };
        continue;
      }

      indexByLabel.set(labelKey, result.length);
      result.push({ label, value });
    }

    return result;
  }

  private joinUniqueTextParts(values: string[], separator: string): string {
    const seen = new Set<string>();
    const result: string[] = [];

    for (const value of values) {
      const clean = this.cleanText(value).replace(/\s+/g, ' ');
      const key = this.normalizeFeatureValue(clean);

      if (!clean || !key || seen.has(key)) {
        continue;
      }

      seen.add(key);
      result.push(clean);
    }

    return result.join(separator);
  }

  private isCameraFrontEntry(entry: FeatureDisplayEntry): boolean {
    const key = this.normalizedDynamicKey(entry.label);
    return key === 'FRONTAL' || key === 'CAMARA FRONTAL';
  }

  private specSectionSortValue(label: string): number {
    const key = this.normalizedDynamicKey(label);
    return this.specSectionOrder[key] ?? Number.MAX_SAFE_INTEGER;
  }

  private normalizedDynamicKey(key: string): string {
    return this.normalizeQ(key).toUpperCase();
  }

  private normalizeFeatureValue(value: string): string {
    return this.normalizeQ(value);
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
    const cuota = this.cuotaTarjeta6(p);
    const sheetEmoji = this.isGoogleSheetProduct(p) ? ` ${this.SHEET_EMOJI}` : '';
    const lines = [
      `Hola! Quiero consultar disponibilidad del ${p.marca} ${p.modelo}.${sheetEmoji}`,
      `- Efectivo: $ ${this.fmtMoney(p.precioPesos)}`,
      `- Transferencia: $ ${this.fmtMoney(p.precioTransferenciaBancaria)}`,
      `- 6 cuotas sin interes de: $ ${this.fmtMoney(cuota)}`
    ];

    if (this.shouldShowColors(p)) {
      lines.push(`Colores: ${this.formatColorsForMessage(p)}`);
    }

    return lines.join('\n');
  }

  private buildWhatsappUrl(p: NuevoProductoLista): string {
    const phone = this.WHATSAPP_PHONE.replace(/[^\d]/g, '');
    const msg = this.buildWhatsappMessage(p);

    const encodedText = encodeURIComponent(msg);
    return `https://wa.me/${phone}?text=${encodedText}`;
  }

  private warningForProduct(p: NuevoProductoLista): { title: string; message: string } | null {
    if (this.isArticulosVarios(p)) {
      return {
        title: 'Confirmar consulta',
        message: 'Este art\u00EDculo en particular requiere pago anticipado. \u00BFDesea continuar?'
      };
    }

    if (this.isPerfume(p)) {
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
        const product = this.mapModel(marca, model, this.mapColors(model?.colores));
        if (product) {
          productos.push(product);
        }
      }
    }

    return this.dedupeProducts(productos);
  }

  private mapModel(
    marca: string,
    model: TiendaPorteModelResponse | null | undefined,
    coloresConStock: ColorStockLista[]
  ): NuevoProductoLista | null {
    const modelo = this.cleanText(model?.modeloNombre);
    if (!modelo) {
      return null;
    }

    const colorSearch = coloresConStock.map(color => color.color).join(' ');
    const baseSearch = `${marca} ${modelo} ${colorSearch}`;
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
      showColorOnCard: model?.showColorOnCard === true,
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
    return this.colorNames(p).join(', ');
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
      const key = this.productKey(product);
      const existing = this.allByKey.get(key);
      this.allByKey.set(key, existing ? this.preferredProductOffer(existing, product) : product);
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

      const preferred = this.preferredProductOffer(existing, product);
      const discarded = preferred === existing ? product : existing;
      if (this.copyMissingOriginFields(preferred, discarded)) {
        changed = true;
      }
      if (preferred !== existing) {
        this.allByKey.set(key, preferred);
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

      const preferred = this.preferredProductOffer(existing, product);
      const discarded = preferred === existing ? product : existing;
      this.copyMissingOriginFields(preferred, discarded);
      map.set(key, preferred);
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
    const colorKey = product.showColorOnCard
      ? this.colorNames(product)
        .map(color => this.normalizeQ(color))
        .filter(Boolean)
        .sort()
        .join('|')
      : '';
    return `${this.normalizeQ(product.marca)}|${this.normalizeQ(product.modelo)}|${
      product.showColorOnCard ? `color:${colorKey || 'sin-color'}` : 'precio-uniforme'
    }`;
  }

  private preferredProductOffer(
    current: NuevoProductoLista,
    candidate: NuevoProductoLista
  ): NuevoProductoLista {
    return this.productOfferPrice(candidate) < this.productOfferPrice(current) ? candidate : current;
  }

  private productOfferPrice(product: NuevoProductoLista): number {
    for (const value of [product.precioUsd, product.precioPesos, product.efectivo]) {
      if (this.hasValidMoney(value) && value >= 0) {
        return value;
      }
    }
    return Number.POSITIVE_INFINITY;
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
