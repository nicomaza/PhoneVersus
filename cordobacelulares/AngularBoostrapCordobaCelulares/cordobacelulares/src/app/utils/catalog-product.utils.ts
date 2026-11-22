export function publicProductSlug(modelName: string | null | undefined): string {
  return (modelName ?? '')
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}

export function hasValidCatalogMoney(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

export function formatCatalogMoney(value: number | null | undefined): string {
  return hasValidCatalogMoney(value)
    ? value.toLocaleString('es-AR', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    })
    : '-';
}

export function tarjeta6Installment(value: number | null | undefined): number | null {
  return hasValidCatalogMoney(value) ? value / 6 : null;
}

export interface CatalogProductTypeRef {
  marca?: string | null;
  modelo?: string | null;
}

export interface CatalogProductWarning {
  title: string;
  message: string;
  confirmLabel?: string;
  emphasized?: boolean;
}

export function isCatalogPerfume(product: CatalogProductTypeRef | null | undefined): boolean {
  return normalizeCatalogCategory(product?.marca) === 'perfumes';
}

export function isCatalogMiscellaneous(product: CatalogProductTypeRef | null | undefined): boolean {
  return normalizeCatalogCategory(product?.marca) === 'articulosvarios';
}

export function isCatalogPhone(product: CatalogProductTypeRef | null | undefined): boolean {
  if (!product || isCatalogPerfume(product) || isCatalogMiscellaneous(product)) {
    return false;
  }
  const model = normalizeCatalogWords(product.modelo);
  const nonPhoneTokens = [
    'camara', 'camera', 'tablet', 'ipad', 'watch', 'reloj', 'airpods', 'auricular',
    'notebook', 'macbook', 'consola', 'joystick', 'parlante', 'tv', 'proyector'
  ];
  if (nonPhoneTokens.some(token => model.includes(token))) {
    return false;
  }
  if (model.includes('iphone')) {
    return true;
  }
  const brand = normalizeCatalogCategory(product.marca);
  return [
    'samsung', 'xiaomi', 'redmi', 'poco', 'motorola', 'moto', 'realme', 'infinix',
    'huawei', 'honor', 'oppo', 'google', 'iphone'
  ].some(token => brand.includes(token));
}

export function catalogProductConditionLabel(
  product: CatalogProductTypeRef | null | undefined
): string {
  if (isCatalogPerfume(product)) {
    return 'En caja sellada de fábrica, sin abrir.';
  }

  if (isCatalogMiscellaneous(product)) {
    return 'Dispositivos nuevos, sellados de fábrica y con garantía.';
  }

  if (!isCatalogPhone(product)) {
    return 'Producto nuevo, original y con garantía.';
  }

  return 'Equipo nuevo, liberado, original, sellado de fábrica y con garantía.';
}

function normalizeCatalogWords(value: string | null | undefined): string {
  return (value ?? '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function catalogProductWhatsappWarning(
  product: CatalogProductTypeRef | null | undefined
): CatalogProductWarning | null {
  if (isCatalogMiscellaneous(product)) {
    return {
      title: 'Confirmar consulta',
      message: 'Este artículo en particular requiere pago anticipado. ¿Desea continuar?'
    };
  }

  if (isCatalogPerfume(product)) {
    return {
      title: 'Confirmar consulta',
      message: 'Los perfumes requieren pago completo anticipado. ¿Desea continuar?'
    };
  }

  if (normalizeCatalogCategory(product?.marca) === 'xiaomi') {
    return {
      title: 'Informaci\u00f3n importante',
      message: 'ESTE EQUIPO ES VERSI\u00d3N GLOBAL\nSOLO VENDEMOS EQUIPOS SELLADOS DE F\u00c1BRICA',
      confirmLabel: 'Entendido, consultar por WhatsApp',
      emphasized: true
    };
  }

  return null;
}

function normalizeCatalogCategory(value: string | null | undefined): string {
  return (value ?? '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, '')
    .trim();
}
