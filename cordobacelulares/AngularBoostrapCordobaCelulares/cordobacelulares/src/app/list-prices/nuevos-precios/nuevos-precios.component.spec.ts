import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { NuevosPreciosService, TiendaPorteBrandResponse } from '../../services/nuevos-precios.service';
import { NuevoProductoLista, NuevosPreciosComponent } from './nuevos-precios.component';

describe('NuevosPreciosComponent catalog variants', () => {
  let fixture: ComponentFixture<NuevosPreciosComponent>;
  let component: NuevosPreciosComponent;

  beforeEach(async () => {
    const service = jasmine.createSpyObj<NuevosPreciosService>('NuevosPreciosService', [
      'getCatalogoPrincipal',
      'getLiberadosYaProductMatch'
    ]);
    service.getCatalogoPrincipal.and.returnValue(of(catalogResponse()));
    service.getLiberadosYaProductMatch.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      imports: [NuevosPreciosComponent],
      providers: [{ provide: NuevosPreciosService, useValue: service }]
    }).compileComponents();

    fixture = TestBed.createComponent(NuevosPreciosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.toggleCategory('IPHONE');
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('renders one card per color with that variant ARS price', () => {
    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('.lp-category-body .lp-card') as NodeListOf<HTMLElement>
    );

    expect(cards.length).toBe(3);
    expect(cardText(cards, 'Cosmic Orange')).toContain('1.240.000');
    expect(cardText(cards, 'Deep Blue')).toContain('1.245.000');
    expect(cardText(cards, 'Silver')).toContain('1.250.000');
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
    expect(modalText).not.toContain('111.111');
  });

  it('keeps transfer and card prices visible for a perfume', () => {
    const perfume = renderSingleProduct('PERFUMES', priceModel('Fragancia floral'));

    component.openDetails(perfume);
    fixture.detectChanges();
    const modalText = fixture.nativeElement.querySelector('.lp-modal-content')?.textContent ?? '';

    expect(modalText).toContain('Transferencia');
    expect(modalText).toContain('222.222');
    expect(modalText).toContain('Tarjeta 6 pagos');
    expect(modalText).toContain('333.333');
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
      .map(label => label.textContent?.trim());

    expect(labels).toEqual(['Efectivo', 'Transferencia', 'Tarjeta 6 pagos']);
    expect(modal.textContent).toContain('111.111');
    expect(modal.textContent).toContain('222.222');
    expect(modal.textContent).toContain('333.333');
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
      expect(row.querySelector('.lp-v')?.textContent?.trim()).toBeTruthy();
    }
    expect(rows[rows.length - 1].matches(':last-child')).toBeTrue();
    expect(getComputedStyle(rows[rows.length - 1]).borderBottomWidth).toBe('0px');
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
      precioTarjeta6Pagos: 333333
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
