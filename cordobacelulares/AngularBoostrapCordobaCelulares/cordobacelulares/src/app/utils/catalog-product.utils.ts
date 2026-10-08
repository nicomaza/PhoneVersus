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
}

export interface CatalogProductWarning {
  title: string;
  message: string;
}

export function isCatalogPerfume(product: CatalogProductTypeRef | null | undefined): boolean {
  return normalizeCatalogCategory(product?.marca) === 'perfumes';
}

export function isCatalogMiscellaneous(product: CatalogProductTypeRef | null | undefined): boolean {
  return normalizeCatalogCategory(product?.marca) === 'articulosvarios';
}

export function isCatalogPhone(product: CatalogProductTypeRef | null | undefined): boolean {
  return !isCatalogPerfume(product) && !isCatalogMiscellaneous(product);
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

  return 'Equipo nuevo, liberado, original, sellado de fábrica y con garantía.';
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
