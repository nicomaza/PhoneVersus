import { CommonModule, DOCUMENT } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  Inject,
  OnDestroy,
  QueryList,
  ViewChild,
  ViewChildren
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Meta, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subscription } from 'rxjs';
import {
  CatalogProductOrigin,
  NuevosPreciosService,
  PublishedProductFeaturesResponse,
  TiendaPorteBrandResponse,
  TiendaPorteColorStockResponse,
  TiendaPorteModelResponse
} from '../../services/nuevos-precios.service';
import { CatalogWhatsappService } from '../../services/catalog-whatsapp.service';
import {
  CatalogFinancingKey,
  CatalogFinancingOption,
  CatalogFinancingService
} from '../../services/catalog-financing.service';
import { HomeBannerService, PublicHomeBanner } from '../../services/home-banner.service';
import {
  CatalogProductWarning,
  catalogProductConditionLabel,
  catalogProductWhatsappWarning,
  formatCatalogMoney,
  isCatalogMiscellaneous,
  isCatalogPerfume,
  publicProductSlug,
  tarjeta6Installment
} from '../../utils/catalog-product.utils';

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
  precioTarjeta1Pago?: number | null;
  precioQr1Pago?: number | null;
  precioPlanZ3Pagos?: number | null;
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
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './nuevos-precios.component.html',
  styleUrl: './nuevos-precios.component.css'
})
export class NuevosPreciosComponent implements AfterViewInit, OnDestroy {
  readonly homeBanners: PublicHomeBanner[] = [];
  currentBannerIndex = 0;
  carouselPointerInside = false;

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
  categorias = [...this.baseCategorias];

  catalogLoading = false;
  catalogLoaded = false;
  catalogErrorMessage: string | null = null;

  searchCtrl = new FormControl<string>('', { nonNullable: true });
  hasSearch = false;

  get searchResultCount(): number {
    return this.grupos.reduce((total, grupo) => total + grupo.items.length, 0);
  }

  @ViewChild('searchInput') private searchInput?: ElementRef<HTMLInputElement>;
  @ViewChild('searchResultsAnchor') private searchResultsAnchor?: ElementRef<HTMLElement>;
  @ViewChild('featuresCloseButton') private featuresCloseButton?: ElementRef<HTMLButtonElement>;
  @ViewChildren('categoryHeader') private categoryHeaders!: QueryList<ElementRef<HTMLButtonElement>>;

  grupos: GrupoMarca[] = [];
  expandedCategory: string | null = null;

  selected: NuevoProductoLista | null = null;
  modalOpen = false;
  financingDropdownOpen = false;
  selectedFinancingKey: CatalogFinancingKey = 'tarjeta6';
  featuresModalOpen = false;
  featuresLoading = false;
  featuresError: FeatureErrorKind | null = null;
  featuresProduct: NuevoProductoLista | null = null;
  featuresImages: string[] = [];
  featuresHighlights: string[] = [];
  featuresImageIndex = 0;
  featuresSpecSections: FeatureDisplaySection[] = [];
  featuresSourceName = '';
  featuresSourceUrl = '';
  hasAvailableCharacteristics = false;
  characteristicsLoaded = false;
  matchedCharacteristicsProduct: PublishedProductFeaturesResponse | null = null;
  warningModalOpen = false;
  pendingWhatsappProduct: NuevoProductoLista | null = null;
  warningTitle = '';
  warningMessage = '';
  warningConfirmLabel = 'Continuar';
  warningEmphasized = false;
  financingAttention = false;
  showFloatingSocial = false;
  searchAttention = false;

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
  private carouselTimer?: ReturnType<typeof setInterval>;
  private carouselManuallyPaused = false;
  private carouselTouchStartX: number | null = null;
  private readonly reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  private readonly previousTitle: string;
  private readonly previousDescription: string | null;
  private readonly previousCanonical: string | null;
  private readonly scrollHandler = (): void => this.handleWindowScroll();
  private readonly failedFeatureImages = new Set<string>();
  private publishedContentSlugs = new Set<string>();
  private characteristicsRequestId = 0;
  private matchedCharacteristicsImages: string[] = [];
  private matchedCharacteristicsHighlights: string[] = [];
  private matchedCharacteristicsSpecSections: FeatureDisplaySection[] = [];
  private matchedCharacteristicsSourceName = '';
  private matchedCharacteristicsSourceUrl = '';
  private featuresImageAlts = new Map<string, string>();
  private matchedCharacteristicsImageAlts = new Map<string, string>();
  private featuresTriggerElement: HTMLElement | null = null;
  private categoryObserver?: IntersectionObserver;
  private readonly categoryHinted = new Set<string>();
  private activeCategoryHint: string | null = null;
  private categoryHintQueue: string[] = [];
  private categoryHintTimer?: ReturnType<typeof setTimeout>;
  private categoryHintGapTimer?: ReturnType<typeof setTimeout>;
  private searchScrollHandled = false;
  private searchAttentionFinished = false;
  private searchAttentionTimer?: ReturnType<typeof setTimeout>;
  private searchAttentionEndTimer?: ReturnType<typeof setTimeout>;
  private searchResultAttentionKey: string | null = null;
  private searchResultAttentionTimer?: ReturnType<typeof setTimeout>;
  private searchResultAttentionEndTimer?: ReturnType<typeof setTimeout>;
  private categoryProductAttentionKey: string | null = null;
  private categoryProductAttentionTimer?: ReturnType<typeof setTimeout>;
  private categoryProductAttentionEndTimer?: ReturnType<typeof setTimeout>;
  private categoryScrollObserved = false;
  private lastCategorySuggestionKey: string | null = null;
  private lastCategorySuggestionAt = 0;
  private financingAttentionTimer?: ReturnType<typeof setTimeout>;
  private financingAttentionEndTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private service: NuevosPreciosService,
    private whatsappService: CatalogWhatsappService,
    private catalogFinancingService: CatalogFinancingService,
    private homeBannerService: HomeBannerService,
    private title: Title,
    private meta: Meta,
    @Inject(DOCUMENT) private document: Document
  ) {
    this.previousTitle = this.title.getTitle();
    this.previousDescription = this.meta.getTag("name='description'")?.content ?? null;
    this.previousCanonical = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.href ?? null;
    this.applyHomeMetadata();
    window.addEventListener('scroll', this.scrollHandler, { passive: true });
    this.showFloatingSocial = window.scrollY > 300;
    this.applyFilter('');
    this.loadCatalogInBackground();
    this.loadPublishedContentSlugs();
    this.loadHomeBanners();

    this.subscriptions.add(
      this.searchCtrl.valueChanges
        .pipe(debounceTime(120), distinctUntilChanged())
        .subscribe(q => {
          this.cancelSearchAttention();
          this.cancelSearchResultAttention();
          this.cancelCategoryProductAttention();
          const hasQuery = !!this.normalizeQ(q);
          this.applyFilter(q, hasQuery && !this.searchScrollHandled);
          this.searchScrollHandled = hasQuery;
          if (hasQuery) {
            this.scheduleSearchResultAttention(this.normalizeQ(q));
          }
        })
    );
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    if (this.financingDropdownOpen) {
      event.preventDefault();
      event.stopPropagation();
      this.financingDropdownOpen = false;
      return;
    }

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

  @HostListener('document:click')
  closeFinancingDropdown(): void {
    this.financingDropdownOpen = false;
  }

  @HostListener('document:pointerdown')
  cancelProductSuggestionOnInteraction(): void {
    this.cancelCategoryProductAttention();
  }

  ngAfterViewInit(): void {
    this.scheduleSearchAttention();
    if (this.reducedMotion || !('IntersectionObserver' in window)) return;
    this.categoryObserver = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        const header = entry.target as HTMLButtonElement;
        const category = header.dataset['category'];
        if (!category || this.isExpanded(category) || this.categoryHinted.has(category)) continue;
        this.categoryHinted.add(category);
        this.categoryObserver?.unobserve(header);
        this.categoryHintQueue.push(category);
      }
      this.startNextCategoryHint();
    }, { threshold: 0.35, rootMargin: '0px 0px -10% 0px' });
    this.observeCategoryHeaders();
    this.subscriptions.add(this.categoryHeaders.changes.subscribe(() => this.observeCategoryHeaders()));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.stopCarousel();
    this.cancelFinancingAttention();
    this.cancelSearchAttention();
    this.cancelSearchResultAttention();
    this.cancelCategoryProductAttention();
    this.cancelCategoryHints();
    this.categoryObserver?.disconnect();
    window.removeEventListener('scroll', this.scrollHandler);
    document.body.classList.remove('modal-open');
    this.restoreMetadata();
  }

  get isCatalogModalOpen(): boolean {
    return this.modalOpen || this.featuresModalOpen || this.warningModalOpen;
  }

  bannerLink(banner: PublicHomeBanner): string | null {
    const value = banner.linkUrl?.trim();
    if (!value) {
      return null;
    }
    if (value.startsWith('/') && !value.startsWith('//')) {
      return value;
    }
    try {
      const url = new URL(value);
      return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null;
    } catch {
      return null;
    }
  }

  selectBanner(index: number): void {
    if (index < 0 || index >= this.homeBanners.length) {
      return;
    }
    this.currentBannerIndex = index;
    this.carouselManuallyPaused = true;
    this.stopCarousel();
  }

  previousBanner(): void {
    const next = this.currentBannerIndex === 0 ? this.homeBanners.length - 1 : this.currentBannerIndex - 1;
    this.selectBanner(next);
  }

  nextBanner(): void {
    this.selectBanner((this.currentBannerIndex + 1) % this.homeBanners.length);
  }

  onCarouselPointer(inside: boolean): void {
    this.carouselPointerInside = inside;
    inside ? this.stopCarousel() : this.startCarousel();
  }

  onCarouselTouchStart(event: TouchEvent): void {
    this.carouselManuallyPaused = true;
    this.stopCarousel();
    this.carouselTouchStartX = event.changedTouches[0]?.clientX ?? null;
  }

  onCarouselTouchEnd(event: TouchEvent): void {
    const endX = event.changedTouches[0]?.clientX;
    if (this.carouselTouchStartX === null || endX === undefined) {
      return;
    }
    const distance = endX - this.carouselTouchStartX;
    this.carouselTouchStartX = null;
    if (Math.abs(distance) < 45) {
      return;
    }
    distance > 0 ? this.previousBanner() : this.nextBanner();
  }

  fmtMoney(n?: number | null): string {
    return formatCatalogMoney(n);
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
    return tarjeta6Installment(p.precioTarjeta6Pagos);
  }

  hasCuotaTarjeta6(p: NuevoProductoLista): boolean {
    return this.cuotaTarjeta6(p) !== null;
  }

  financingOptions(p: NuevoProductoLista): CatalogFinancingOption[] {
    return this.isPerfume(p) ? [] : this.catalogFinancingService.options(p);
  }

  selectedFinancing(p: NuevoProductoLista): CatalogFinancingOption | null {
    return this.financingOptions(p)
      .find(option => option.key === this.selectedFinancingKey) ?? null;
  }

  toggleFinancingDropdown(event: MouseEvent, hasOptions: boolean): void {
    event.stopPropagation();
    this.cancelFinancingAttention();
    if (hasOptions) {
      this.financingDropdownOpen = !this.financingDropdownOpen;
    }
  }

  selectFinancing(option: CatalogFinancingOption, event: MouseEvent): void {
    event.stopPropagation();
    this.cancelFinancingAttention();
    this.selectedFinancingKey = option.key;
    this.financingDropdownOpen = false;
  }

  isPerfume(p: NuevoProductoLista | null | undefined): boolean {
    return isCatalogPerfume(p);
  }

  isArticulosVarios(p: NuevoProductoLista | null | undefined): boolean {
    return isCatalogMiscellaneous(p);
  }

  shouldShowColors(p: NuevoProductoLista | null | undefined): boolean {
    return !!p && !this.isPerfume(p) && this.colorNames(p).length > 0;
  }

  modalSubtitle(p: NuevoProductoLista | null | undefined): string {
    return this.shouldShowColors(p) ? '' : '';
  }

  cardHint(p: NuevoProductoLista | null | undefined): string {
    if (this.isPerfume(p)) {
      return 'Tocar para ver precio por transferencia';
    }
    return this.shouldShowColors(p)
      ? 'Tocar para ver transferencia, tarjeta y colores'
      : 'Tocar para ver transferencia y tarjeta';
  }

  isCashBestPrice(p: NuevoProductoLista): boolean {
    if (this.isPerfume(p) || !this.hasValidMoney(p.precioPesos)) {
      return false;
    }
    const otherPrices = [
      p.precioTransferenciaBancaria,
      ...this.financingOptions(p).map(option => option.total)
    ].filter((value): value is number => this.hasValidMoney(value));
    return otherPrices.every(value => p.precioPesos! <= value);
  }

  productConditionLabel(p: NuevoProductoLista | null | undefined): string {
    return catalogProductConditionLabel(p);
  }

  isGoogleSheetProduct(p: NuevoProductoLista | null | undefined): boolean {
    return this.whatsappService.isGoogleSheetProduct(p);
  }

  getSheetEmoji(p: NuevoProductoLista | null | undefined): string {
    return this.isGoogleSheetProduct(p) ? this.whatsappService.sheetEmoji : '';
  }

  productSlug(p: NuevoProductoLista): string {
    return publicProductSlug(p.modelo);
  }

  productHref(p: NuevoProductoLista): string {
    return `/celulares/${this.productSlug(p)}`;
  }

  hasPublishedContent(p: NuevoProductoLista): boolean {
    return this.publishedContentSlugs.has(this.productSlug(p));
  }

  onProductCardClick(event: MouseEvent, p: NuevoProductoLista): void {
    this.cancelSearchResultAttention();
    this.cancelCategoryProductAttention();
    const isModifiedClick = event.ctrlKey || event.metaKey || event.shiftKey || event.altKey;
    if (event.button !== 0 || isModifiedClick) {
      return;
    }

    event.preventDefault();
    this.openDetails(p);
  }

  openDetails(p: NuevoProductoLista): void {
    this.cancelSearchResultAttention();
    this.cancelCategoryProductAttention();
    this.resetCharacteristicsState();
    this.selected = p;
    this.selectedFinancingKey = 'tarjeta6';
    this.financingDropdownOpen = false;
    if (!this.selectedFinancing(p)) {
      this.selectedFinancingKey = this.financingOptions(p)[0]?.key ?? 'tarjeta6';
    }
    this.modalOpen = true;
    document.body.classList.add('modal-open');
    this.scheduleFinancingAttention(p);
    this.preloadCharacteristics(p);
  }

  closeModal(): void {
    this.closeFeaturesModal(false);
    this.resetCharacteristicsState();
    this.modalOpen = false;
    this.financingDropdownOpen = false;
    this.cancelFinancingAttention();
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
    this.featuresImages = [...this.matchedCharacteristicsImages];
    this.featuresHighlights = [...this.matchedCharacteristicsHighlights];
    this.featuresImageIndex = 0;
    this.featuresSpecSections = this.cloneFeatureSections(this.matchedCharacteristicsSpecSections);
    this.featuresSourceName = this.matchedCharacteristicsSourceName;
    this.featuresSourceUrl = this.matchedCharacteristicsSourceUrl;
    this.featuresImageAlts = new Map(this.matchedCharacteristicsImageAlts);
    this.failedFeatureImages.clear();
    this.featuresModalOpen = true;
    document.body.classList.add('modal-open');
    requestAnimationFrame(() => this.featuresCloseButton?.nativeElement.focus());
  }

  closeFeaturesModal(restoreFocus = true): void {
    if (!this.featuresModalOpen && !this.featuresProduct && !this.featuresLoading) {
      return;
    }

    this.featuresModalOpen = false;
    this.featuresLoading = false;
    this.featuresError = null;
    this.featuresProduct = null;
    this.featuresImages = [];
    this.featuresHighlights = [];
    this.featuresImageIndex = 0;
    this.featuresSpecSections = [];
    this.featuresSourceName = '';
    this.featuresSourceUrl = '';
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

    this.featuresImages = [...this.matchedCharacteristicsImages];
    this.featuresHighlights = [...this.matchedCharacteristicsHighlights];
    this.featuresSpecSections = this.cloneFeatureSections(this.matchedCharacteristicsSpecSections);
    this.featuresSourceName = this.matchedCharacteristicsSourceName;
    this.featuresSourceUrl = this.matchedCharacteristicsSourceUrl;
    this.featuresImageAlts = new Map(this.matchedCharacteristicsImageAlts);
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

  hasGsmaArenaFeaturesSource(): boolean {
    if (this.featuresSourceName.trim().toLowerCase() !== 'gsmarena') return false;
    try {
      const url = new URL(this.featuresSourceUrl);
      const host = url.hostname.toLowerCase();
      return url.protocol === 'https:' && (host === 'gsmarena.com' || host.endsWith('.gsmarena.com'));
    } catch {
      return false;
    }
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
    this.featuresImageAlts.delete(imageUrl);
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
    const current = this.featureCurrentImage();
    const storedAlt = current ? this.featuresImageAlts.get(current)?.trim() : '';
    if (storedAlt) return storedAlt;
    const product = this.featuresProduct ?? this.selected;
    const name = `${product?.marca ?? ''} ${product?.modelo ?? ''}`.trim();
    return name ? `Imagen de ${name}` : 'Imagen del producto';
  }

  hasFeaturesContent(): boolean {
    return this.featuresImages.length > 0
      || this.featuresHighlights.length > 0
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
    const slug = this.productSlug(p);
    if (!slug || !this.publishedContentSlugs.has(slug)) {
      this.characteristicsLoaded = true;
      return;
    }

    const requestId = ++this.characteristicsRequestId;
    const selectedKey = this.productKey(p);

    this.subscriptions.add(
      this.service.getPublishedProductFeatures(slug).subscribe({
        next: response => {
          if (!this.isActiveCharacteristicsRequest(requestId, selectedKey)) {
            return;
          }

          const imageRecords = [...(response.images ?? [])]
            .filter(image => !!image?.publicPath?.trim())
            .sort((a, b) => Number(b.primaryImage) - Number(a.primaryImage));
          const images = Array.from(new Set(imageRecords.map(image => image.publicPath.trim())));
          const highlights = (response.highlights ?? [])
            .map(item => item?.text?.trim() ?? '')
            .filter(Boolean);
          const specSections = (response.specSections ?? [])
            .map(section => ({
              label: section?.title?.trim() ?? '',
              entries: (section?.specs ?? [])
                .map(spec => ({ label: spec?.label?.trim() ?? '', value: spec?.value?.trim() ?? '' }))
                .filter(spec => !!spec.label && !!spec.value)
            }))
            .filter(section => !!section.label && section.entries.length > 0);

          this.matchedCharacteristicsProduct = response;
          this.matchedCharacteristicsImages = images;
          this.matchedCharacteristicsHighlights = highlights;
          this.matchedCharacteristicsSpecSections = specSections;
          this.matchedCharacteristicsSourceName = response.sourceName?.trim() ?? '';
          this.matchedCharacteristicsSourceUrl = response.sourceUrl?.trim() ?? '';
          this.matchedCharacteristicsImageAlts = new Map(
            imageRecords.map(image => [image.publicPath.trim(), image.altText?.trim() ?? ''])
          );
          this.hasAvailableCharacteristics = images.length > 0 || highlights.length > 0 || specSections.length > 0;
        },
        error: () => {
          if (!this.isActiveCharacteristicsRequest(requestId, selectedKey)) {
            return;
          }

          this.characteristicsLoaded = true;
          this.hasAvailableCharacteristics = false;
          this.matchedCharacteristicsProduct = null;
          this.matchedCharacteristicsImages = [];
          this.matchedCharacteristicsHighlights = [];
          this.matchedCharacteristicsSpecSections = [];
          this.matchedCharacteristicsSourceName = '';
          this.matchedCharacteristicsSourceUrl = '';
          this.matchedCharacteristicsImageAlts.clear();
        },
        complete: () => {
          if (this.isActiveCharacteristicsRequest(requestId, selectedKey)) {
            this.characteristicsLoaded = true;
          }
        }
      })
    );
  }

  private resetCharacteristicsState(): void {
    this.characteristicsRequestId++;
    this.characteristicsLoaded = false;
    this.hasAvailableCharacteristics = false;
    this.matchedCharacteristicsProduct = null;
    this.matchedCharacteristicsImages = [];
    this.matchedCharacteristicsHighlights = [];
    this.matchedCharacteristicsSpecSections = [];
    this.matchedCharacteristicsSourceName = '';
    this.matchedCharacteristicsSourceUrl = '';
    this.matchedCharacteristicsImageAlts.clear();
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

  abrirWhatsApp(p: NuevoProductoLista): void {
    const warning = this.warningForProduct(p);
    if (warning) {
      this.openWarningModal(p, warning);
      return;
    }

    this.openWhatsApp(p);
  }

  clearSearch(): void {
    this.cancelSearchResultAttention();
    this.cancelCategoryProductAttention();
    this.searchScrollHandled = false;
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
    this.whatsappService.openProductConsultation(p, this.selectedFinancing(p));
  }

  private warningForProduct(p: NuevoProductoLista): CatalogProductWarning | null {
    return catalogProductWhatsappWarning(p);
  }

  private openWarningModal(
    product: NuevoProductoLista,
    warning: CatalogProductWarning
  ): void {
    this.pendingWhatsappProduct = product;
    this.warningTitle = warning.title;
    this.warningMessage = warning.message;
    this.warningConfirmLabel = warning.confirmLabel ?? 'Continuar';
    this.warningEmphasized = warning.emphasized === true;
    this.warningModalOpen = true;
    document.body.classList.add('modal-open');
  }

  private closeWarningModal(): void {
    this.warningModalOpen = false;
    this.pendingWhatsappProduct = null;
    this.warningTitle = '';
    this.warningMessage = '';
    this.warningConfirmLabel = 'Continuar';
    this.warningEmphasized = false;

    if (!this.modalOpen) {
      document.body.classList.remove('modal-open');
    }
  }

  private scheduleFinancingAttention(product: NuevoProductoLista): void {
    this.cancelFinancingAttention();
    if (this.reducedMotion || this.financingOptions(product).length <= 1) {
      return;
    }

    this.financingAttentionTimer = setTimeout(() => {
      if (!this.modalOpen || this.financingDropdownOpen || this.selected !== product) {
        return;
      }
      this.financingAttention = true;
      this.financingAttentionEndTimer = setTimeout(() => {
        this.financingAttention = false;
      }, 1200);
    }, 500);
  }

  private cancelFinancingAttention(): void {
    if (this.financingAttentionTimer) {
      clearTimeout(this.financingAttentionTimer);
      this.financingAttentionTimer = undefined;
    }
    if (this.financingAttentionEndTimer) {
      clearTimeout(this.financingAttentionEndTimer);
      this.financingAttentionEndTimer = undefined;
    }
    this.financingAttention = false;
  }

  toggleCategory(categoria: string): void {
    this.stopCategoryHint(categoria);
    this.cancelCategoryProductAttention();
    if (this.expandedCategory === categoria) {
      this.expandedCategory = null;
      this.categoryScrollObserved = false;
      this.startNextCategoryHint();
      return;
    }

    this.expandedCategory = categoria;
    this.categoryScrollObserved = false;
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

  isCategoryHinted(categoria: string): boolean {
    return this.activeCategoryHint === categoria;
  }

  onSearchInteraction(): void {
    this.cancelSearchAttention();
    this.cancelSearchResultAttention();
    this.cancelCategoryProductAttention();
  }

  productAnimationKey(product: NuevoProductoLista): string {
    return this.productKey(product);
  }

  isSearchResultAttention(product: NuevoProductoLista): boolean {
    return this.searchResultAttentionKey === this.productKey(product);
  }

  isCategoryProductAttention(product: NuevoProductoLista): boolean {
    return this.categoryProductAttentionKey === this.productKey(product);
  }

  categoryPanelId(categoria: string): string {
    return `category-${this.normalizeQ(categoria).replace(/[^a-z0-9]+/g, '-')}`;
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

  private loadPublishedContentSlugs(): void {
    this.subscriptions.add(this.service.getPublishedProductSlugs().subscribe({
      next: slugs => this.publishedContentSlugs = new Set(slugs),
      error: () => this.publishedContentSlugs.clear()
    }));
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
    this.showFloatingSocial = window.scrollY > 300;
    this.cancelCategoryProductAttention();
    const categoria = this.expandedCategory;
    if (categoria && !this.hasSearch) {
      this.categoryScrollObserved = true;
      this.scheduleCategoryProductAttention();
    }
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
      precioTarjeta1Pago: this.safeMoney(model?.precioTarjeta1Pago),
      precioQr1Pago: this.safeMoney(model?.precioQr1Pago),
      precioPlanZ3Pagos: this.safeMoney(model?.precioPlanZ3Pagos),
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

  private applyFilter(qRaw: string, scrollToTop = true): void {
    const qNorm = this.normalizeQ(qRaw ?? '');
    this.hasSearch = !!qNorm;

    if (!qNorm) {
      this.grupos = [];
      this.cancelSearchResultAttention();
      return;
    }

    const ranked = this.smartSearch(this.all, qRaw);
    this.grupos = this.groupByBrand(ranked);

    if (scrollToTop) requestAnimationFrame(() => this.scrollToSearchResults());
  }

  private scrollToSearchResults(): void {
    const anchor = this.searchResultsAnchor?.nativeElement;
    if (!anchor) return;
    const search = this.document.querySelector<HTMLElement>('.lp-search');
    const container = this.document.querySelector<HTMLElement>('.lp-container');
    const topOffset = container
      ? Number.parseFloat(getComputedStyle(container).getPropertyValue('--lp-top-offset')) || 0
      : 0;
    const desiredTop = topOffset + (search?.offsetHeight ?? 0) + 12;
    const anchorTop = anchor.getBoundingClientRect().top;
    if (anchorTop >= desiredTop && anchorTop <= window.innerHeight * .45) return;
    window.scrollTo({
      top: Math.max(0, window.scrollY + anchorTop - desiredTop),
      behavior: this.reducedMotion ? 'auto' : 'smooth'
    });
  }

  private observeCategoryHeaders(): void {
    if (!this.categoryObserver) return;
    for (const reference of this.categoryHeaders) {
      const header = reference.nativeElement;
      const category = header.dataset['category'];
      if (category && !this.categoryHinted.has(category)) this.categoryObserver.observe(header);
    }
  }

  private scheduleSearchAttention(): void {
    if (this.reducedMotion || this.searchAttentionFinished || this.searchAttentionTimer) {
      return;
    }
    this.searchAttentionTimer = setTimeout(() => {
      this.searchAttentionTimer = undefined;
      if (this.searchAttentionFinished || this.searchCtrl.value.trim()) {
        return;
      }
      this.searchAttention = true;
      this.searchAttentionEndTimer = setTimeout(() => {
        this.searchAttention = false;
        this.searchAttentionFinished = true;
        this.searchAttentionEndTimer = undefined;
      }, 2500);
    }, 2000);
  }

  private cancelSearchAttention(): void {
    if (this.searchAttentionTimer) {
      clearTimeout(this.searchAttentionTimer);
      this.searchAttentionTimer = undefined;
    }
    if (this.searchAttentionEndTimer) {
      clearTimeout(this.searchAttentionEndTimer);
      this.searchAttentionEndTimer = undefined;
    }
    this.searchAttention = false;
    this.searchAttentionFinished = true;
  }

  private scheduleSearchResultAttention(query: string): void {
    if (this.reducedMotion || !query) {
      return;
    }
    this.searchResultAttentionTimer = setTimeout(() => {
      this.searchResultAttentionTimer = undefined;
      if (this.isCatalogModalOpen || this.normalizeQ(this.searchCtrl.value) !== query || this.searchResultCount === 0) {
        return;
      }
      const cards = Array.from(this.document.querySelectorAll<HTMLElement>('.lp-search-product-card'));
      const card = cards.find(candidate => this.isElementVisible(candidate));
      const productKey = card?.dataset['productKey'];
      if (!productKey) {
        return;
      }
      this.searchResultAttentionKey = productKey;
      this.searchResultAttentionEndTimer = setTimeout(() => {
        this.searchResultAttentionKey = null;
        this.searchResultAttentionEndTimer = undefined;
      }, 2500);
    }, 550);
  }

  private cancelSearchResultAttention(): void {
    if (this.searchResultAttentionTimer) {
      clearTimeout(this.searchResultAttentionTimer);
      this.searchResultAttentionTimer = undefined;
    }
    if (this.searchResultAttentionEndTimer) {
      clearTimeout(this.searchResultAttentionEndTimer);
      this.searchResultAttentionEndTimer = undefined;
    }
    this.searchResultAttentionKey = null;
  }

  private startNextCategoryHint(): void {
    if (this.reducedMotion || this.activeCategoryHint || this.categoryHintTimer
        || this.expandedCategory || this.hasSearch || this.isCatalogModalOpen) {
      return;
    }
    const category = this.categoryHintQueue.shift();
    if (!category) {
      return;
    }
    this.activeCategoryHint = category;
    this.categoryHintTimer = setTimeout(() => {
      this.activeCategoryHint = null;
      this.categoryHintTimer = undefined;
      this.categoryHintGapTimer = setTimeout(() => {
        this.categoryHintGapTimer = undefined;
        this.startNextCategoryHint();
      }, 150);
    }, 2500);
  }

  private stopCategoryHint(category: string): void {
    this.categoryHintQueue = this.categoryHintQueue.filter(candidate => candidate !== category);
    if (this.activeCategoryHint !== category) {
      return;
    }
    if (this.categoryHintTimer) {
      clearTimeout(this.categoryHintTimer);
      this.categoryHintTimer = undefined;
    }
    this.activeCategoryHint = null;
  }

  private cancelCategoryHints(): void {
    if (this.categoryHintTimer) clearTimeout(this.categoryHintTimer);
    if (this.categoryHintGapTimer) clearTimeout(this.categoryHintGapTimer);
    this.categoryHintTimer = undefined;
    this.categoryHintGapTimer = undefined;
    this.activeCategoryHint = null;
    this.categoryHintQueue = [];
  }

  private scheduleCategoryProductAttention(): void {
    const category = this.expandedCategory;
    if (this.reducedMotion || !category || !this.categoryScrollObserved || this.hasSearch
        || this.isCatalogModalOpen || this.categoryProductAttentionTimer) {
      return;
    }
    const remainingInterval = Math.max(0, 5000 - (Date.now() - this.lastCategorySuggestionAt));
    this.categoryProductAttentionTimer = setTimeout(() => {
      this.categoryProductAttentionTimer = undefined;
      if (this.expandedCategory !== category || this.hasSearch || this.isCatalogModalOpen) {
        return;
      }
      const cards = Array.from(this.document.querySelectorAll<HTMLElement>('.lp-category-product-card'));
      const visibleCards = cards.filter(candidate => this.isElementFullyVisible(candidate));
      const candidates = visibleCards.length > 1
        ? visibleCards.filter(candidate => candidate.dataset['productKey'] !== this.lastCategorySuggestionKey)
        : visibleCards;
      const card = candidates[Math.floor(Math.random() * candidates.length)];
      const productKey = card?.dataset['productKey'];
      if (!productKey) {
        return;
      }
      this.categoryProductAttentionKey = productKey;
      this.lastCategorySuggestionKey = productKey;
      this.lastCategorySuggestionAt = Date.now();
      this.categoryProductAttentionEndTimer = setTimeout(() => {
        this.categoryProductAttentionKey = null;
        this.categoryProductAttentionEndTimer = undefined;
      }, 2500);
    }, Math.max(1500, remainingInterval));
  }

  private cancelCategoryProductAttention(): void {
    if (this.categoryProductAttentionTimer) {
      clearTimeout(this.categoryProductAttentionTimer);
      this.categoryProductAttentionTimer = undefined;
    }
    if (this.categoryProductAttentionEndTimer) {
      clearTimeout(this.categoryProductAttentionEndTimer);
      this.categoryProductAttentionEndTimer = undefined;
    }
    this.categoryProductAttentionKey = null;
  }

  private isElementVisible(element: HTMLElement): boolean {
    const rect = element.getBoundingClientRect();
    return rect.height > 0 && rect.width > 0 && rect.bottom > 0 && rect.top < window.innerHeight;
  }

  private isElementFullyVisible(element: HTMLElement): boolean {
    const rect = element.getBoundingClientRect();
    return rect.height > 0 && rect.width > 0 && rect.top >= 0 && rect.bottom <= window.innerHeight
      && rect.left >= 0 && rect.right <= window.innerWidth;
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
    if (this.expandedCategory === categoria) {
      this.scheduleCategoryProductAttention();
    }
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

  private loadHomeBanners(): void {
    this.subscriptions.add(
      this.homeBannerService.listPublic().subscribe({
        next: banners => {
          this.homeBanners.splice(0, this.homeBanners.length, ...banners);
          this.currentBannerIndex = 0;
          this.startCarousel();
        },
        error: () => {
          this.homeBanners.splice(0);
          this.stopCarousel();
        }
      })
    );
  }

  private startCarousel(): void {
    this.stopCarousel();
    if (this.reducedMotion || this.carouselManuallyPaused || this.carouselPointerInside || this.homeBanners.length < 2) {
      return;
    }
    this.carouselTimer = setInterval(() => {
      this.currentBannerIndex = (this.currentBannerIndex + 1) % this.homeBanners.length;
    }, 5000);
  }

  private stopCarousel(): void {
    if (this.carouselTimer !== undefined) {
      clearInterval(this.carouselTimer);
      this.carouselTimer = undefined;
    }
  }

  private applyHomeMetadata(): void {
    const pageTitle = 'Celulares nuevos y liberados en C\u00F3rdoba | C\u00F3rdoba Celulares';
    const description = 'Celulares nuevos y liberados en C\u00F3rdoba, sellados de f\u00E1brica y con garant\u00EDa. Consult\u00E1 modelos, stock y precios actualizados.';
    const canonicalUrl = 'https://cordobacelulares.com/';
    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:title', content: pageTitle }, "property='og:title'");
    this.meta.updateTag({ property: 'og:description', content: description }, "property='og:description'");
    this.meta.updateTag({ property: 'og:url', content: canonicalUrl }, "property='og:url'");
    this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.setAttribute('href', canonicalUrl);
  }

  private restoreMetadata(): void {
    this.title.setTitle(this.previousTitle);
    if (this.previousDescription === null) {
      this.meta.removeTag("name='description'");
    } else {
      this.meta.updateTag({ name: 'description', content: this.previousDescription });
    }
    const canonical = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (canonical && this.previousCanonical) {
      canonical.setAttribute('href', this.previousCanonical);
    }
  }
}
