import { Injectable, isDevMode } from '@angular/core';
import { CatalogProductOrigin } from './nuevos-precios.service';
import { formatCatalogMoney, tarjeta6Installment } from '../utils/catalog-product.utils';

export interface CatalogWhatsappFinancing {
  label: string;
  total: number;
  installments?: number | null;
  installmentAmount?: number | null;
}

export interface CatalogWhatsappProduct {
  marca: string;
  modelo: string;
  origen?: CatalogProductOrigin | null;
  source?: CatalogProductOrigin | boolean | null;
  origin?: CatalogProductOrigin | boolean | null;
  provider?: CatalogProductOrigin | boolean | null;
  proveedor?: CatalogProductOrigin | boolean | null;
  precioPesos?: number | null;
  precioTransferenciaBancaria?: number | null;
  precioTarjeta1Pago?: number | null;
  precioQr1Pago?: number | null;
  precioPlanZ3Pagos?: number | null;
  precioTarjeta3Pagos?: number | null;
  precioTarjeta6Pagos?: number | null;
  colores?: string[];
  coloresConStock?: Array<{ color: string; stock?: number | null }>;
}

@Injectable({
  providedIn: 'root'
})
export class CatalogWhatsappService {
  readonly sheetEmoji = String.fromCodePoint(0x1F4F2);

  private readonly whatsappPhone = '5493512129922';

  openProductConsultation(
    product: CatalogWhatsappProduct,
    financing?: CatalogWhatsappFinancing | null
  ): void {
    const message = this.buildProductMessage(product, financing);
    const url = this.buildUrl(message);

    if (isDevMode()) {
      console.debug('[catalogo][wa-msg]', message);
      console.debug('[catalogo][wa-url]', url);
    }

    window.open(url, '_blank', 'noopener,noreferrer');
  }

  openGeneralConsultation(productLabel?: string): void {
    const cleanLabel = (productLabel ?? '').trim();
    const message = cleanLabel
      ? `Hola! Quiero consultar por el modelo ${cleanLabel}.`
      : 'Hola! Quiero consultar por un modelo de celular.';

    window.open(this.buildUrl(message), '_blank', 'noopener,noreferrer');
  }

  openHomeConsultation(): void {
    const message = 'Hola! Estoy viendo la p\u00E1gina de C\u00F3rdoba Celulares y quisiera hacer una consulta.';
    window.open(this.buildUrl(message), '_blank', 'noopener,noreferrer');
  }

  buildProductMessage(
    product: CatalogWhatsappProduct,
    financing?: CatalogWhatsappFinancing | null
  ): string {
    const sheetEmoji = this.isGoogleSheetProduct(product) ? ` ${this.sheetEmoji}` : '';
    const heading = `Hola! Quiero consultar disponibilidad del ${product.marca.trim()} ${product.modelo.trim()}.${sheetEmoji}`;

    if (this.isPerfume(product)) {
      return [
        heading,
        `- Transferencia: $ ${formatCatalogMoney(product.precioTransferenciaBancaria)}`
      ].join('\n');
    }

    const installment = tarjeta6Installment(product.precioTarjeta6Pagos);
    const lines = [
      heading,
      `- Efectivo: $ ${formatCatalogMoney(product.precioPesos)}`,
      `- Transferencia: $ ${formatCatalogMoney(product.precioTransferenciaBancaria)}`,
      financing
        ? this.financingLine(financing)
        : `- 6 cuotas sin interes de: $ ${formatCatalogMoney(installment)}`
    ];

    const colors = this.colorNames(product);
    if (this.shouldShowColors(product, colors)) {
      lines.push(`Colores: ${colors.join(', ')}`);
    }

    return lines.join('\n');
  }

  buildProductUrl(
    product: CatalogWhatsappProduct,
    financing?: CatalogWhatsappFinancing | null
  ): string {
    return this.buildUrl(this.buildProductMessage(product, financing));
  }

  isGoogleSheetProduct(product: CatalogWhatsappProduct | null | undefined): boolean {
    const raw = String(
      product?.origen ??
      product?.source ??
      product?.origin ??
      product?.provider ??
      product?.proveedor ??
      ''
    ).trim().toUpperCase();
    const normalized = this.normalizeSignal(raw);

    return raw === 'GOOGLE_SHEET'
      || normalized === 'GOOGLESHEET'
      || raw === 'SUPPLIER_SHEET'
      || normalized === 'SUPPLIERSHEET'
      || raw === 'SHEET'
      || normalized === 'SHEET';
  }

  private buildUrl(message: string): string {
    const phone = this.whatsappPhone.replace(/[^\d]/g, '');
    return `https://wa.me/${phone}?text=${encodeURIComponent(message)}`;
  }

  private financingLine(financing: CatalogWhatsappFinancing): string {
    const total = `$ ${formatCatalogMoney(financing.total)}`;
    const hasInstallments = typeof financing.installments === 'number'
      && financing.installments > 1
      && typeof financing.installmentAmount === 'number'
      && Number.isFinite(financing.installmentAmount);

    return hasInstallments
      ? `- ${financing.label}: ${total} (${financing.installments} cuotas de $ ${formatCatalogMoney(financing.installmentAmount)})`
      : `- ${financing.label}: ${total}`;
  }

  private colorNames(product: CatalogWhatsappProduct): string[] {
    const stockColors = (product.coloresConStock ?? [])
      .map(item => (item.color ?? '').trim())
      .filter(Boolean);

    if (stockColors.length > 0) {
      return stockColors;
    }

    return (product.colores ?? [])
      .map(color => (color ?? '').trim())
      .filter(Boolean);
  }

  private shouldShowColors(product: CatalogWhatsappProduct, colors: string[]): boolean {
    return !this.isPerfume(product) && colors.length > 0;
  }

  private isPerfume(product: CatalogWhatsappProduct): boolean {
    return this.normalizeSignal(product.marca) === 'PERFUMES';
  }

  private normalizeSignal(value: string | null | undefined): string {
    return (value ?? '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9]/g, '')
      .toUpperCase();
  }
}
