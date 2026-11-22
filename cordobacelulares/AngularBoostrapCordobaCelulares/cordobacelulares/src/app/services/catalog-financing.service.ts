import { Injectable } from '@angular/core';
import { TiendaPorteBrandResponse, TiendaPorteModelResponse } from './nuevos-precios.service';

export type CatalogFinancingKey = 'tarjeta1' | 'qr1' | 'planZ3' | 'tarjeta3' | 'tarjeta6';

export interface CatalogFinancingOption {
  key: CatalogFinancingKey;
  label: string;
  total: number;
  installments: number | null;
  installmentAmount: number | null;
}

export interface CatalogPaymentProfile {
  transferMultiplier: number | null;
  financingMultipliers: Partial<Record<CatalogFinancingKey, number>>;
}

export interface CatalogPaymentSimulation {
  cash: number;
  transfer: number | null;
  financing: CatalogFinancingOption[];
}

type FinancingField = keyof Pick<
  TiendaPorteModelResponse,
  'precioTarjeta1Pago' | 'precioQr1Pago' | 'precioPlanZ3Pagos' | 'precioTarjeta3Pagos' | 'precioTarjeta6Pagos'
>;

export type CatalogFinancingPrices = Pick<TiendaPorteModelResponse, FinancingField>;

interface FinancingDefinition {
  key: CatalogFinancingKey;
  label: string;
  field: FinancingField;
  installments?: number;
}

const FINANCING_DEFINITIONS: readonly FinancingDefinition[] = [
  { key: 'tarjeta1', label: 'Tarjeta 1 pago', field: 'precioTarjeta1Pago' },
  { key: 'qr1', label: 'QR 1 pago', field: 'precioQr1Pago' },
  { key: 'planZ3', label: 'Plan Z · 3 pagos', field: 'precioPlanZ3Pagos', installments: 3 },
  { key: 'tarjeta3', label: 'Tarjeta 3 cuotas', field: 'precioTarjeta3Pagos', installments: 3 },
  { key: 'tarjeta6', label: 'Tarjeta 6 cuotas', field: 'precioTarjeta6Pagos', installments: 6 }
];

@Injectable({ providedIn: 'root' })
export class CatalogFinancingService {
  options(product: CatalogFinancingPrices): CatalogFinancingOption[] {
    return FINANCING_DEFINITIONS
      .map(definition => this.option(definition, product[definition.field]))
      .filter((option): option is CatalogFinancingOption => option !== null);
  }

  profileFromCatalog(brands: TiendaPorteBrandResponse[]): CatalogPaymentProfile | null {
    const models = (brands ?? []).flatMap(brand => brand.modelos ?? []);
    const transferMultiplier = this.findMultiplier(models, 'precioTransferenciaBancaria');
    const financingMultipliers: Partial<Record<CatalogFinancingKey, number>> = {};

    for (const definition of FINANCING_DEFINITIONS) {
      const multiplier = this.findMultiplier(models, definition.field);
      if (multiplier !== null) financingMultipliers[definition.key] = multiplier;
    }

    return transferMultiplier === null && Object.keys(financingMultipliers).length === 0
      ? null
      : { transferMultiplier, financingMultipliers };
  }

  simulate(basePrice: number, profile: CatalogPaymentProfile): CatalogPaymentSimulation | null {
    if (!this.validPositiveMoney(basePrice)) return null;

    const calculatedProduct: CatalogFinancingPrices = {};
    for (const definition of FINANCING_DEFINITIONS) {
      const multiplier = profile.financingMultipliers[definition.key];
      if (this.validPositiveMoney(multiplier)) {
        calculatedProduct[definition.field] = this.roundMoney(basePrice * multiplier);
      }
    }

    return {
      cash: this.roundMoney(basePrice),
      transfer: this.validPositiveMoney(profile.transferMultiplier)
        ? this.roundMoney(basePrice * profile.transferMultiplier)
        : null,
      financing: this.options(calculatedProduct)
    };
  }

  private option(
    definition: FinancingDefinition,
    total: number | null | undefined
  ): CatalogFinancingOption | null {
    if (!this.validPositiveMoney(total)) return null;
    const installments = definition.installments ?? null;
    return {
      key: definition.key,
      label: definition.label,
      total,
      installments,
      installmentAmount: installments ? total / installments : null
    };
  }

  private findMultiplier(
    models: TiendaPorteModelResponse[],
    field: FinancingField | 'precioTransferenciaBancaria'
  ): number | null {
    for (const model of models) {
      const base = model.precioPesos;
      const calculated = model[field];
      if (this.validPositiveMoney(base) && this.validPositiveMoney(calculated)) {
        return calculated / base;
      }
    }
    return null;
  }

  private validPositiveMoney(value: number | null | undefined): value is number {
    return typeof value === 'number' && Number.isFinite(value) && value > 0;
  }

  private roundMoney(value: number): number {
    return Math.round((value + Number.EPSILON) * 100) / 100;
  }
}
