import { CommonModule, DOCUMENT } from '@angular/common';
import { Component, HostListener, Inject, OnDestroy, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, distinctUntilChanged, map, of, Subscription, switchMap } from 'rxjs';
import {
  NuevosPreciosService,
  TiendaPorteBrandResponse,
  TiendaPorteModelResponse
} from '../services/nuevos-precios.service';
import { CatalogWhatsappService } from '../services/catalog-whatsapp.service';
import {
  catalogProductConditionLabel,
  catalogProductWhatsappWarning,
  formatCatalogMoney,
  hasValidCatalogMoney,
  isCatalogPhone,
  publicProductSlug,
  tarjeta6Installment
} from '../utils/catalog-product.utils';

interface PublicCatalogProduct extends TiendaPorteModelResponse {
  marca: string;
  modelo: string;
}

interface MetaSnapshot {
  title: string;
  description: string | null;
  ogTitle: string | null;
  ogDescription: string | null;
  ogUrl: string | null;
  ogType: string | null;
}

interface ProductLoadResult {
  product: PublicCatalogProduct | null;
  technicalError: boolean;
}

@Component({
  selector: 'app-producto-publico',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './producto-publico.component.html',
  styleUrl: './producto-publico.component.css'
})
export class ProductoPublicoComponent implements OnInit, OnDestroy {
  loading = true;
  loadError = false;
  product: PublicCatalogProduct | null = null;
  requestedSlug = '';
  warningModalOpen = false;
  pendingWhatsappProduct: PublicCatalogProduct | null = null;
  warningTitle = '';
  warningMessage = '';

  private readonly subscriptions = new Subscription();
  private readonly metaSnapshot: MetaSnapshot;
  private canonicalLink: HTMLLinkElement | null = null;
  private canonicalCreated = false;
  private previousCanonicalHref: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private nuevosPreciosService: NuevosPreciosService,
    private whatsappService: CatalogWhatsappService,
    private title: Title,
    private meta: Meta,
    @Inject(DOCUMENT) private document: Document
  ) {
    this.metaSnapshot = {
      title: this.title.getTitle(),
      description: this.metaContent("name='description'"),
      ogTitle: this.metaContent("property='og:title'"),
      ogDescription: this.metaContent("property='og:description'"),
      ogUrl: this.metaContent("property='og:url'"),
      ogType: this.metaContent("property='og:type'")
    };
  }

  ngOnInit(): void {
    this.subscriptions.add(
      this.route.paramMap.pipe(
        map(params => (params.get('slug') ?? '').trim().toLowerCase()),
        distinctUntilChanged(),
        switchMap(slug => {
          this.loading = true;
          this.loadError = false;
          this.product = null;
          this.requestedSlug = slug;

          return this.nuevosPreciosService.getCatalogoPrincipal().pipe(
            map(catalog => ({
              product: this.findProduct(catalog, slug),
              technicalError: false
            } satisfies ProductLoadResult)),
            catchError(() => of({
              product: null,
              technicalError: true
            } satisfies ProductLoadResult))
          );
        })
      ).subscribe(result => {
        this.product = result.product;
        this.loadError = result.technicalError;
        this.loading = false;

        if (result.technicalError) {
          this.applyTechnicalErrorMetadata();
        } else if (result.product) {
          this.applyProductMetadata(result.product);
        } else {
          this.applyUnavailableMetadata();
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.document.body.classList.remove('modal-open');
    this.removeProductJsonLd();
    this.restoreCanonical();
    this.restoreMetadata();
  }

  fmtPrice(value: number | null | undefined): string {
    return `$ ${formatCatalogMoney(value)}`;
  }

  hasPrice(value: number | null | undefined): value is number {
    return hasValidCatalogMoney(value);
  }

  cuotaTarjeta6(product: PublicCatalogProduct): number | null {
    return tarjeta6Installment(product.precioTarjeta6Pagos);
  }

  productConditionLabel(product: PublicCatalogProduct): string {
    return catalogProductConditionLabel(product);
  }

  isPhoneProduct(product: PublicCatalogProduct): boolean {
    return isCatalogPhone(product);
  }

  colorNames(product: PublicCatalogProduct): string[] {
    const unique = new Map<string, string>();
    for (const item of product.colores ?? []) {
      const color = (item?.color ?? '').trim();
      const key = color.toLocaleLowerCase('es-AR');
      if (color && !unique.has(key)) {
        unique.set(key, color);
      }
    }
    return Array.from(unique.values());
  }

  consultarProducto(product: PublicCatalogProduct): void {
    const warning = catalogProductWhatsappWarning(product);
    if (warning) {
      this.pendingWhatsappProduct = product;
      this.warningTitle = warning.title;
      this.warningMessage = warning.message;
      this.warningModalOpen = true;
      this.document.body.classList.add('modal-open');
      return;
    }

    this.openProductWhatsapp(product);
  }

  cancelWarningModal(): void {
    this.closeWarningModal();
  }

  confirmWarningWhatsapp(): void {
    const product = this.pendingWhatsappProduct;
    this.closeWarningModal();
    if (product) {
      this.openProductWhatsapp(product);
    }
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    if (!this.warningModalOpen) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    this.closeWarningModal();
  }

  private openProductWhatsapp(product: PublicCatalogProduct): void {
    this.whatsappService.openProductConsultation({
      ...product,
      colores: this.colorNames(product)
    });
  }

  consultarProductoNoDisponible(): void {
    const label = this.requestedSlug.replace(/-/g, ' ');
    this.whatsappService.openGeneralConsultation(label);
  }

  private closeWarningModal(): void {
    this.warningModalOpen = false;
    this.pendingWhatsappProduct = null;
    this.warningTitle = '';
    this.warningMessage = '';
    this.document.body.classList.remove('modal-open');
  }

  private findProduct(
    catalog: TiendaPorteBrandResponse[],
    slug: string
  ): PublicCatalogProduct | null {
    if (!slug) {
      return null;
    }

    for (const brand of catalog) {
      const marca = (brand?.marca ?? '').trim() || 'Sin marca';
      for (const model of brand?.modelos ?? []) {
        const modelo = (model?.modeloNombre ?? '').trim();
        if (modelo && publicProductSlug(modelo) === slug) {
          return { ...model, marca, modelo };
        }
      }
    }

    return null;
  }

  private applyProductMetadata(product: PublicCatalogProduct): void {
    const canonicalUrl = this.canonicalUrl(this.requestedSlug);
    const pageTitle = `${product.modelo}: precio en Córdoba | Córdoba Celulares`;
    const description = `Consultá el precio actualizado del ${product.modelo} en Córdoba Celulares. Efectivo, transferencia y tarjeta. Equipos nuevos y con garantía.`;

    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:title', content: pageTitle }, "property='og:title'");
    this.meta.updateTag({ property: 'og:description', content: description }, "property='og:description'");
    this.meta.updateTag({ property: 'og:url', content: canonicalUrl }, "property='og:url'");
    this.meta.updateTag({ property: 'og:type', content: 'product' }, "property='og:type'");
    this.updateCanonical(canonicalUrl);
    this.updateProductJsonLd(product, canonicalUrl);
  }

  private applyUnavailableMetadata(): void {
    const pageTitle = 'Producto no disponible | Córdoba Celulares';
    const description = 'Este modelo no se encuentra disponible actualmente en nuestro catálogo.';
    const canonicalUrl = this.canonicalUrl(this.requestedSlug);

    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:title', content: pageTitle }, "property='og:title'");
    this.meta.updateTag({ property: 'og:description', content: description }, "property='og:description'");
    this.meta.updateTag({ property: 'og:url', content: canonicalUrl }, "property='og:url'");
    this.meta.updateTag({ property: 'og:type', content: 'website' }, "property='og:type'");
    this.updateCanonical(canonicalUrl);
    this.removeProductJsonLd();
  }

  private applyTechnicalErrorMetadata(): void {
    this.removeProductJsonLd();
  }

  private updateCanonical(url: string): void {
    if (!this.canonicalLink) {
      this.canonicalLink = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
      if (this.canonicalLink) {
        this.previousCanonicalHref = this.canonicalLink.getAttribute('href');
      } else {
        this.canonicalLink = this.document.createElement('link');
        this.canonicalLink.id = 'producto-publico-canonical';
        this.canonicalLink.rel = 'canonical';
        this.document.head.appendChild(this.canonicalLink);
        this.canonicalCreated = true;
      }
    }

    this.canonicalLink.setAttribute('href', url);
  }

  private updateProductJsonLd(product: PublicCatalogProduct, url: string): void {
    let script = this.document.head.querySelector<HTMLScriptElement>('#producto-publico-jsonld');
    if (!script) {
      script = this.document.createElement('script');
      script.id = 'producto-publico-jsonld';
      script.type = 'application/ld+json';
      this.document.head.appendChild(script);
    }

    const jsonLd: Record<string, unknown> = {
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: product.modelo,
      brand: {
        '@type': 'Brand',
        name: product.marca
      }
    };

    if (hasValidCatalogMoney(product.precioPesos)) {
      jsonLd['offers'] = {
        '@type': 'Offer',
        url,
        priceCurrency: 'ARS',
        price: product.precioPesos
      };
    }

    script.textContent = JSON.stringify(jsonLd);
  }

  private removeProductJsonLd(): void {
    this.document.head.querySelector<HTMLScriptElement>('#producto-publico-jsonld')?.remove();
  }

  private restoreCanonical(): void {
    if (!this.canonicalLink) {
      return;
    }

    if (this.canonicalCreated) {
      this.canonicalLink.remove();
    } else if (this.previousCanonicalHref === null) {
      this.canonicalLink.removeAttribute('href');
    } else {
      this.canonicalLink.setAttribute('href', this.previousCanonicalHref);
    }
  }

  private restoreMetadata(): void {
    this.title.setTitle(this.metaSnapshot.title || 'Córdoba Celulares');
    this.restoreMetaTag('description', this.metaSnapshot.description, false);
    this.restoreMetaTag('og:title', this.metaSnapshot.ogTitle, true);
    this.restoreMetaTag('og:description', this.metaSnapshot.ogDescription, true);
    this.restoreMetaTag('og:url', this.metaSnapshot.ogUrl, true);
    this.restoreMetaTag('og:type', this.metaSnapshot.ogType, true);
  }

  private restoreMetaTag(key: string, content: string | null, isProperty: boolean): void {
    const selector = isProperty ? `property='${key}'` : `name='${key}'`;
    if (content === null) {
      this.meta.removeTag(selector);
      return;
    }

    this.meta.updateTag(
      isProperty ? { property: key, content } : { name: key, content },
      selector
    );
  }

  private metaContent(selector: string): string | null {
    return this.meta.getTag(selector)?.getAttribute('content') ?? null;
  }

  private canonicalUrl(slug: string): string {
    return `https://cordobacelulares.com/celulares/${encodeURIComponent(slug)}`;
  }
}
