import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { CatalogWhatsappService } from '../../services/catalog-whatsapp.service';
import { HomeBannerService } from '../../services/home-banner.service';
import { NuevosPreciosService, TiendaPorteBrandResponse } from '../../services/nuevos-precios.service';
import { NuevoProductoLista, NuevosPreciosComponent } from './nuevos-precios.component';

describe('NuevosPreciosComponent catalog variants', () => {
  let fixture: ComponentFixture<NuevosPreciosComponent>;
  let component: NuevosPreciosComponent;
  let whatsappService: jasmine.SpyObj<CatalogWhatsappService>;
  let nuevosPreciosService: jasmine.SpyObj<NuevosPreciosService>;

  beforeEach(async () => {
    nuevosPreciosService = jasmine.createSpyObj<NuevosPreciosService>('NuevosPreciosService', [
      'getCatalogoPrincipal',
      'getPublishedProductFeatures',
      'getPublishedProductSlugs',
      'hasCatalogCache',
      'getCategoryFromCatalog',
      'getCachedCategory'
    ]);
    nuevosPreciosService.getCatalogoPrincipal.and.returnValue(of(catalogResponse()));
    nuevosPreciosService.getPublishedProductFeatures.and.returnValue(of({ highlights: [], specSections: [], images: [] }));
    nuevosPreciosService.getPublishedProductSlugs.and.returnValue(of([]));
    nuevosPreciosService.hasCatalogCache.and.returnValue(true);
    nuevosPreciosService.getCategoryFromCatalog.and.callFake(category =>
      catalogResponse().filter(group => group.marca === category)
    );
    nuevosPreciosService.getCachedCategory.and.returnValue(null);

    const homeBannerService = jasmine.createSpyObj<HomeBannerService>('HomeBannerService', ['listPublic']);
    homeBannerService.listPublic.and.returnValue(of([]));

    whatsappService = jasmine.createSpyObj<CatalogWhatsappService>(
      'CatalogWhatsappService',
      ['openProductConsultation', 'openHomeConsultation', 'isGoogleSheetProduct'],
      { sheetEmoji: '📲' }
    );
    whatsappService.isGoogleSheetProduct.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [NuevosPreciosComponent],
      providers: [
        { provide: NuevosPreciosService, useValue: nuevosPreciosService },
        { provide: HomeBannerService, useValue: homeBannerService },
        { provide: CatalogWhatsappService, useValue: whatsappService },
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NuevosPreciosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.toggleCategory('IPHONE');
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('shows both floating social buttons only after scrolling and hides them for catalog modals', () => {
    let scrollY = 0;
    spyOnProperty(window, 'scrollY', 'get').and.callFake(() => scrollY);

    (component as any).handleWindowScroll();
    fixture.detectChanges();
    expect(component.showFloatingSocial).toBeFalse();
    expect(fixture.nativeElement.querySelector('.home-instagram')?.classList.contains('home-social-visible')).toBeFalse();
    expect(fixture.nativeElement.querySelector('.home-whatsapp')?.classList.contains('home-social-visible')).toBeFalse();

    scrollY = 301;
    (component as any).handleWindowScroll();
    fixture.detectChanges();
    expect(component.showFloatingSocial).toBeTrue();
    expect(fixture.nativeElement.querySelector('.home-instagram')?.classList.contains('home-social-visible')).toBeTrue();
    expect(fixture.nativeElement.querySelector('.home-whatsapp')?.classList.contains('home-social-visible')).toBeTrue();

    component.modalOpen = true;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.home-instagram')).toBeNull();
    expect(fixture.nativeElement.querySelector('.home-whatsapp')).toBeNull();

    component.modalOpen = false;
    scrollY = 0;
    (component as any).handleWindowScroll();
    expect(component.showFloatingSocial).toBeFalse();
  });

  it('scrolls to a new search only once per search session', fakeAsync(() => {
    const scrollSpy = spyOn<any>(component, 'scrollToSearchResults');

    component.searchCtrl.setValue('iphone');
    tick(150);
    tick(20);
    component.searchCtrl.setValue('iphone 17');
    tick(150);
    tick(20);

    expect(scrollSpy).toHaveBeenCalledTimes(1);
  }));

  it('exposes category expansion state and the one-time interaction hint accessibly', () => {
    component.toggleCategory('IPHONE');
    (component as any).categoryHinted.add('XIAOMI');
    fixture.detectChanges();

    const xiaomi = fixture.nativeElement.querySelector('[data-category="XIAOMI"]') as HTMLButtonElement;
    expect(xiaomi.classList.contains('lp-category-hint')).toBeTrue();
    expect(xiaomi.getAttribute('aria-expanded')).toBe('false');

    xiaomi.click();
    fixture.detectChanges();
    expect(xiaomi.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector(`#${component.categoryPanelId('XIAOMI')}`)).not.toBeNull();
  });

  it('renders one card per color with that variant ARS price', () => {
    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('.lp-category-body .lp-card') as NodeListOf<HTMLElement>
    );

    expect(cards.length).toBe(3);
    expect(cardText(cards, 'Cosmic Orange')).toContain('1.240.000');
    expect(cardText(cards, 'Deep Blue')).toContain('1.245.000');
    expect(cardText(cards, 'Silver')).toContain('1.250.000');
  });

  it('loads the published local ficha without consulting an external catalog', () => {
    const product = (component as any).all[0] as NuevoProductoLista;
    const slug = component.productSlug(product);
    (component as any).publishedContentSlugs = new Set([slug]);
    nuevosPreciosService.getPublishedProductFeatures.and.returnValue(of({
      sourceName: 'GSMArena',
      sourceUrl: 'https://www.gsmarena.com/test_phone-1.php',
      highlights: [{ text: 'Pantalla AMOLED' }],
      specSections: [{ title: 'Pantalla', specs: [{ label: 'Tamaño', value: '6.7 pulgadas' }] }],
      images: [{ publicPath: '/product-media/modelo.webp', altText: 'Vista frontal', primaryImage: true }]
    }));

    component.openDetails(product);
    component.openFeaturesModal(product);
    fixture.detectChanges();

    expect(nuevosPreciosService.getPublishedProductFeatures).toHaveBeenCalledOnceWith(slug);
    const modalText = fixture.nativeElement.querySelector('.lp-features-content')?.textContent ?? '';
    expect(modalText).toContain('Pantalla AMOLED');
    expect(modalText).toContain('6.7 pulgadas');
    expect(fixture.nativeElement.querySelector('.lp-feature-image')?.getAttribute('src'))
      .toContain('/product-media/modelo.webp');
    const sourceLink = fixture.nativeElement.querySelector('.lp-feature-info-note a') as HTMLAnchorElement;
    expect(sourceLink?.textContent?.trim()).toBe('GSMArena');
    expect(sourceLink?.getAttribute('href')).toBe('https://www.gsmarena.com/test_phone-1.php');
  });

  it('uses different render keys for colors and keeps the cheapest same-color duplicate', () => {
    const products = (component as unknown as { all: NuevoProductoLista[] }).all;
    const keys = products.map((product, index) => component.trackByProduct(index, product));
    const cosmic = products.find(product => component.cardColor(product) === 'Cosmic Orange');

    expect(products.length).toBe(3);
    expect(new Set(keys).size).toBe(3);
    expect(cosmic?.precioUsd).toBe(1240);
    expect(products.map(product => component.cardColor(product)))
      .toEqual(jasmine.arrayWithExactContents(['Cosmic Orange', 'Deep Blue', 'Silver']));
  });

  it('renders one colorless card for a uniform price and keeps every color in details', () => {
    const products = (component as any).mapResponse([{
      marca: 'IPHONE',
      modelos: [{
        modeloNombre: 'IPHONE 17 PRO MAX 512GB',
        showColorOnCard: false,
        colores: [
          { color: 'Cosmic Orange', stock: 2 },
          { color: 'Deep Blue', stock: 1 }
        ],
        precioUsd: 2547.90,
        precioPesos: 2547900
      }]
    }]) as NuevoProductoLista[];

    (component as any).setCategoryProducts('IPHONE', products);
    fixture.detectChanges();

    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('.lp-category-body .lp-card') as NodeListOf<HTMLElement>
    );
    expect(cards.length).toBe(1);
    expect(cards[0].textContent).not.toContain('Cosmic Orange');
    expect(cards[0].textContent).not.toContain('Deep Blue');

    component.openDetails(products[0]);
    fixture.detectChanges();
    const modalText = fixture.nativeElement.querySelector('.lp-modal-content')?.textContent ?? '';
    expect(modalText).toContain('Cosmic Orange');
    expect(modalText).toContain('Deep Blue');
  });

  it('renders the three reported sheet products with their exact prices', () => {
    const products = (component as any).mapResponse([{
      marca: 'OTROS',
      modelos: [
        {
          modeloNombre: 'Amazon Fire Tv Stick 4K Select 8GB Wi Fi 5',
          colores: [],
          precioUsd: 50,
          precioPesos: 50000
        },
        {
          modeloNombre: 'Amazon Kindle Gen 11 – 16GB',
          colores: [{ color: 'Matcha', stock: null }],
          precioUsd: 149,
          precioPesos: 149000
        },
        {
          modeloNombre: 'Anthbot Genie 600 – Robot Cortacésped Inteligente',
          colores: [],
          precioUsd: 1250,
          precioPesos: 1250000
        }
      ]
    }]) as NuevoProductoLista[];

    (component as any).replaceMemory(products);
    (component as any).refreshDynamicCategories();
    (component as any).refreshCategoriesFromCatalog();
    component.toggleCategory('OTROS');
    fixture.detectChanges();

    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('.lp-category-body .lp-card') as NodeListOf<HTMLElement>
    );
    const cardText = cards.map(card => (card.textContent ?? '').replace(/\s+/g, ' '));

    expect(cards.length).toBe(3);
    expect(cardText.some(text => text.includes('Amazon Fire Tv Stick 4K Select 8GB Wi Fi 5') && text.includes('50.000'))).toBeTrue();
    expect(cardText.some(text => text.includes('Amazon Kindle Gen 11 – 16GB') && text.includes('149.000'))).toBeTrue();
    expect(cardText.some(text => text.includes('Anthbot Genie 600 – Robot Cortacésped Inteligente') && text.includes('1.250.000'))).toBeTrue();
  });

  it('hides the cash label and amount for a perfume in category and search cards and in details', () => {
    const perfume = renderSingleProduct('  perfúmes  ', priceModel('Fragancia floral'), 'PERFUMES');

    let card = fixture.nativeElement.querySelector('.lp-category-body .lp-card') as HTMLElement;
    expect(card.querySelector('.lp-price-line')).toBeNull();
    expect(card.textContent).not.toContain('Efectivo');
    expect(card.textContent).not.toContain('111.111');

    (component as any).applyFilter('Fragancia floral', false);
    fixture.detectChanges();
    card = fixture.nativeElement.querySelector('.lp-brand-block .lp-card') as HTMLElement;
    expect(card.querySelector('.lp-price-line')).toBeNull();
    expect(card.textContent).not.toContain('Efectivo');
    expect(card.textContent).not.toContain('111.111');

    component.openDetails(perfume);
    fixture.detectChanges();
    const modalText = fixture.nativeElement.querySelector('.lp-modal-content')?.textContent ?? '';
    expect(modalText).not.toContain('Efectivo');
  });

  it('keeps transfer and card prices visible for a perfume', () => {
    const perfume = renderSingleProduct('PERFUMES', priceModel('Fragancia floral'));

    component.openDetails(perfume);
    fixture.detectChanges();
    const modalText = fixture.nativeElement.querySelector('.lp-modal-content')?.textContent ?? '';

    expect(modalText).toContain('Transferencia');
    expect(modalText).toContain('222.222');
    expect(modalText).toContain('Tarjeta 6 cuotas');
    expect(modalText).toContain('666.666');
  });

  it('keeps all three prices visible for a non-perfume product', () => {
    const phone = renderSingleProduct('SAMSUNG', priceModel('Galaxy de prueba'));
    const card = fixture.nativeElement.querySelector('.lp-category-body .lp-card') as HTMLElement;

    expect(card.textContent).toContain('Efectivo');
    expect(card.textContent).toContain('111.111');

    component.openDetails(phone);
    fixture.detectChanges();
    const modal = fixture.nativeElement.querySelector('.lp-modal-content') as HTMLElement;
    const labels = Array.from(modal.querySelectorAll('.lp-k'))
      .map(label => label.firstElementChild?.textContent?.trim() ?? label.textContent?.trim());

    expect(labels).toEqual(['Efectivo', 'Transferencia', 'Financiación']);
    expect(modal.textContent).toContain('111.111');
    expect(modal.textContent).toContain('222.222');
    expect(modal.textContent).toContain('666.666');
  });

  it('removes the whole cash blocks for a perfume without leaving empty rows or separators', () => {
    const perfume = renderSingleProduct('PERFUMES', priceModel('Fragancia floral'));
    const card = fixture.nativeElement.querySelector('.lp-category-body .lp-card') as HTMLElement;

    expect(card.querySelectorAll('.lp-price-line').length).toBe(0);

    component.openDetails(perfume);
    fixture.detectChanges();
    const rows = Array.from(
      fixture.nativeElement.querySelectorAll('.lp-prices > .lp-price-row') as NodeListOf<HTMLElement>
    );

    expect(rows.length).toBe(2);
    for (const row of rows) {
      expect(row.querySelector('.lp-k')?.textContent?.trim()).toBeTruthy();
      expect(row.querySelector('.lp-v, .lp-financing-trigger')?.textContent?.trim()).toBeTruthy();
    }
    expect(rows[rows.length - 1].matches(':last-child')).toBeTrue();
    expect(getComputedStyle(rows[rows.length - 1]).borderBottomWidth).toBe('0px');
  });

  it('shows every valid financing option with its total and installments', () => {
    const phone = renderSingleProduct('SAMSUNG', priceModel('Galaxy financiado'));
    component.openDetails(phone);
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector('.lp-financing-trigger') as HTMLButtonElement;
    expect(trigger.textContent).toContain('Tarjeta 6 cuotas');
    expect(trigger.textContent).toContain('666.666');
    expect(trigger.textContent).toContain('6 cuotas de $ 111.111');

    trigger.click();
    fixture.detectChanges();
    const optionText = Array.from(
      fixture.nativeElement.querySelectorAll('.lp-financing-option') as NodeListOf<HTMLElement>
    ).map(option => (option.textContent ?? '').replace(/\s+/g, ' ').trim());

    expect(optionText).toEqual([
      'Tarjeta 1 pago$ 300.000',
      'QR 1 pago$ 310.000',
      'Plan Z · 3 pagos$ 330.000 (3 cuotas de $ 110.000)',
      'Tarjeta 3 cuotas$ 360.000 (3 cuotas de $ 120.000)',
      'Tarjeta 6 cuotas$ 666.666 (6 cuotas de $ 111.111)'
    ]);
  });

  it('shows compact commercial notes without the previous conditions list', () => {
    const phone = renderSingleProduct('SAMSUNG', priceModel('Galaxy compacto'));
    component.openDetails(phone);
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('.lp-modal-content') as HTMLElement;
    expect(modal.textContent).toContain('Todos los precios expresados en pesos argentinos (ARS)');
    expect(modal.textContent).toContain('Aceptamos TODAS las tarjetas');
    expect(modal.textContent).toContain('Equipo nuevo, liberado, original, sellado de f\u00e1brica y con garant\u00eda.');
    expect(modal.textContent).toContain('Atenci\u00f3n con turno previo \u00b7 Aceptamos d\u00f3lares y criptomonedas.');
    expect(modal.querySelector('.lp-legend-title')).toBeNull();
    expect(modal.querySelector('.lp-legend-list')).toBeNull();
  });

  it('shows the centralized warning only for the exact normalized Xiaomi brand', () => {
    const xiaomi = renderSingleProduct('  XIAOMI  ', priceModel('Xiaomi global'), 'XIAOMI');
    component.openDetails(xiaomi);
    component.abrirWhatsApp(xiaomi);
    fixture.detectChanges();

    expect(component.warningModalOpen).toBeTrue();
    expect(component.warningTitle).toBe('Informaci\u00f3n importante');
    expect(component.warningMessage).toContain('ESTE EQUIPO ES VERSI\u00d3N GLOBAL');
    expect(component.warningMessage).toContain('SOLO VENDEMOS EQUIPOS SELLADOS DE F\u00c1BRICA');
    expect(component.warningConfirmLabel).toBe('Entendido, consultar por WhatsApp');

    component.confirmWarningWhatsapp();
    expect(whatsappService.openProductConsultation).toHaveBeenCalledTimes(1);
    expect(whatsappService.openProductConsultation).toHaveBeenCalledWith(
      xiaomi,
      jasmine.objectContaining({ label: 'Tarjeta 6 cuotas' })
    );
    whatsappService.openProductConsultation.calls.reset();

    for (const brand of ['REDMI', 'POCO', 'SAMSUNG']) {
      const product = (component as any).mapResponse([{
        marca: brand,
        modelos: [priceModel(`${brand} prueba`)]
      }])[0] as NuevoProductoLista;
      component.abrirWhatsApp(product);
      expect(component.warningModalOpen).toBeFalse();
    }
    expect(whatsappService.openProductConsultation).toHaveBeenCalledTimes(3);
  });

  it('keeps the existing perfume warning unchanged', () => {
    const perfume = renderSingleProduct('PERFUMES', priceModel('Fragancia floral'));
    component.abrirWhatsApp(perfume);

    expect(component.warningModalOpen).toBeTrue();
    expect(component.warningTitle).toBe('Confirmar consulta');
    expect(component.warningMessage).toContain('pago completo anticipado');
    expect(component.warningConfirmLabel).toBe('Continuar');
    expect(whatsappService.openProductConsultation).not.toHaveBeenCalled();
  });

  it('runs the financing attention once after the delay and cancels it on interaction', fakeAsync(() => {
    const phone = renderSingleProduct('SAMSUNG', priceModel('Galaxy animado'));
    (component as any).reducedMotion = false;

    component.openDetails(phone);
    expect(component.financingAttention).toBeFalse();
    tick(500);
    expect(component.financingAttention).toBeTrue();
    tick(1200);
    expect(component.financingAttention).toBeFalse();

    component.closeModal();
    component.openDetails(phone);
    component.toggleFinancingDropdown(new MouseEvent('click'), true);
    tick(500);
    expect(component.financingAttention).toBeFalse();

    component.closeModal();
    (component as any).reducedMotion = true;
    component.openDetails(phone);
    tick(500);
    expect(component.financingAttention).toBeFalse();
  }));

  it('omits invalid financing amounts and resets the next product to Tarjeta 6 cuotas', () => {
    const first = renderSingleProduct('SAMSUNG', {
      ...priceModel('Galaxy uno'),
      precioQr1Pago: 0,
      precioPlanZ3Pagos: null
    });
    component.openDetails(first);
    component.selectFinancing(component.financingOptions(first)[0], new MouseEvent('click'));
    expect(component.selectedFinancing(first)?.label).toBe('Tarjeta 1 pago');
    expect(component.financingOptions(first).map(option => option.label)).not.toContain('QR 1 pago');
    expect(component.financingOptions(first).map(option => option.label)).not.toContain('Plan Z · 3 pagos');

    const second = (component as any).mapResponse([{
      marca: 'MOTOROLA',
      modelos: [priceModel('Moto dos')]
    }])[0] as NuevoProductoLista;
    component.openDetails(second);

    expect(component.selectedFinancing(second)?.label).toBe('Tarjeta 6 cuotas');
  });

  it('keeps the selected financing through the warning flow before WhatsApp', () => {
    const product = renderSingleProduct('ARTICULOS VARIOS', priceModel('Accesorio'));
    component.openDetails(product);
    const planZ = component.financingOptions(product)
      .find(option => option.label === 'Plan Z · 3 pagos')!;
    component.selectFinancing(planZ, new MouseEvent('click'));

    component.abrirWhatsApp(product);
    expect(component.warningModalOpen).toBeTrue();
    component.confirmWarningWhatsapp();

    expect(whatsappService.openProductConsultation).toHaveBeenCalledWith(
      product,
      jasmine.objectContaining({
        label: 'Plan Z · 3 pagos',
        total: 330000,
        installments: 3,
        installmentAmount: 110000
      })
    );
  });

  it('renders all eight Google Sheet products inside the SONY category', () => {
    const models = [
      'EA Sports FC 26 – PS5',
      'JOYSTICK PS5 INALÁMBRICO SONY PLAYSTATION 5 GOD OF WAR EDITION LIMITED 20th ANNIVERSARY',
      'PLAYSTATION 5 SLIM 825GB PS5 DIGITAL + DUALSENSE',
      'PS5 CON LECTORA 1TB',
      'PlayStation 3 500GB Con flash incluido + juegos digitales',
      'PlayStation 5 Pro 2TB Digital',
      'PlayStation 5 Slim 825GB Digital en stock',
      'joystick PS5 dualsence'
    ];
    const products = (component as any).mapResponse([{
      marca: 'SONY',
      modelos: models.map((modeloNombre, index) => ({
        modeloNombre,
        origen: 'GOOGLE_SHEET',
        colores: [],
        precioUsd: index === 0 ? 53 : (index === 5 ? 1300 : 100 + index),
        precioPesos: index === 0 ? 53000 : (index === 5 ? 1300000 : 100000 + index)
      }))
    }]) as NuevoProductoLista[];

    (component as any).replaceMemory(products);
    (component as any).refreshDynamicCategories();
    (component as any).refreshCategoriesFromCatalog();
    component.toggleCategory('SONY');
    fixture.detectChanges();

    const renderedModels = Array.from(
      fixture.nativeElement.querySelectorAll('.lp-category-body .lp-model') as NodeListOf<HTMLElement>
    ).map(element => element.textContent?.trim());

    expect(component.categorias).toContain('SONY');
    expect(renderedModels).toEqual(jasmine.arrayWithExactContents(models));
  });

  function renderSingleProduct(
    category: string,
    model: Record<string, unknown>,
    expandedCategory = category.trim()
  ): NuevoProductoLista {
    const products = (component as any).mapResponse([{
      marca: category,
      modelos: [model]
    }]) as NuevoProductoLista[];

    (component as any).replaceMemory(products);
    (component as any).refreshDynamicCategories();
    (component as any).refreshCategoriesFromCatalog();
    component.toggleCategory(expandedCategory);
    fixture.detectChanges();
    return products[0];
  }

  function priceModel(modeloNombre: string): Record<string, unknown> {
    return {
      modeloNombre,
      colores: [],
      precioPesos: 111111,
      precioTransferenciaBancaria: 222222,
      precioTarjeta1Pago: 300000,
      precioQr1Pago: 310000,
      precioPlanZ3Pagos: 330000,
      precioTarjeta3Pagos: 360000,
      precioTarjeta6Pagos: 666666
    };
  }

  function cardText(cards: HTMLElement[], color: string): string {
    const card = cards.find(candidate => candidate.textContent?.includes(color));
    expect(card).toBeDefined();
    return (card?.textContent ?? '').replace(/\s+/g, ' ');
  }

  function catalogResponse(): TiendaPorteBrandResponse[] {
    return [{
      marca: 'IPHONE',
      modelos: [
        variant('Cosmic Orange', 1260, 1260000),
        variant('Deep Blue', 1245, 1245000),
        variant('Cosmic Orange', 1240, 1240000),
        variant('Silver', 1250, 1250000)
      ]
    }];
  }

  function variant(color: string, precioUsd: number, precioPesos: number) {
    return {
      modeloNombre: 'IPHONE 17 PRO MAX 256GB',
      showColorOnCard: true,
      colores: [{ color, stock: 1 }],
      precioUsd,
      precioPesos,
      precioTransferenciaBancaria: precioPesos,
      precioTarjeta6Pagos: precioPesos
    };
  }
});
