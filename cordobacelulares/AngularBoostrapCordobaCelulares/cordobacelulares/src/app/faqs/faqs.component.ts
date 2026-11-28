import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  CatalogCombinedBalanceMethod,
  CatalogCombinedPaymentSimulation,
  CatalogFinancingOption,
  CatalogFinancingService,
  CatalogPaymentProfile,
  CatalogPaymentSimulation
} from '../services/catalog-financing.service';
import { CatalogWhatsappService } from '../services/catalog-whatsapp.service';
import { NuevosPreciosService } from '../services/nuevos-precios.service';

@Component({
  selector: 'app-faqs',
  standalone: true,
  imports: [RouterLink, CommonModule, ReactiveFormsModule],
  templateUrl: './faqs.component.html',
  styleUrl: './faqs.component.css'
})
export class FaqsComponent implements OnInit, OnDestroy {
  openFaq: string | null = null;
  pricingLoading = true;
  pricingError = '';
  priceTouched = false;
  combinedTotalTouched = false;
  combinedCardTouched = false;
  simulation: CatalogPaymentSimulation | null = null;
  combinedSimulation: CatalogCombinedPaymentSimulation | null = null;
  readonly priceControl = new FormControl<number | null>(null, [
    Validators.required,
    Validators.min(1)
  ]);
  readonly combinedTotalControl = new FormControl<number | null>(null, [
    Validators.required,
    Validators.min(1)
  ]);
  readonly combinedCardControl = new FormControl<number | null>(null, [
    Validators.required,
    Validators.min(1)
  ]);
  readonly combinedBalanceMethodControl = new FormControl<CatalogCombinedBalanceMethod>('cash', {
    nonNullable: true
  });

  private paymentProfile: CatalogPaymentProfile | null = null;
  private readonly subscriptions = new Subscription();

  constructor(
    private nuevosPreciosService: NuevosPreciosService,
    private financingService: CatalogFinancingService,
    private whatsappService: CatalogWhatsappService
  ) {}

  ngOnInit(): void {
    this.subscriptions.add(this.priceControl.valueChanges.subscribe(() => this.updateSimulation()));
    this.subscriptions.add(this.combinedTotalControl.valueChanges.subscribe(() => this.updateCombinedSimulation()));
    this.subscriptions.add(this.combinedCardControl.valueChanges.subscribe(() => this.updateCombinedSimulation()));
    this.subscriptions.add(this.combinedBalanceMethodControl.valueChanges.subscribe(() => this.updateCombinedSimulation()));
    this.subscriptions.add(this.nuevosPreciosService.getCatalogoPrincipal().subscribe({
      next: catalog => {
        this.paymentProfile = this.financingService.profileFromCatalog(catalog);
        this.pricingLoading = false;
        this.pricingError = this.paymentProfile
          ? ''
          : 'No pudimos obtener las modalidades de pago actuales. Intentá nuevamente más tarde.';
        this.updateSimulation();
        this.updateCombinedSimulation();
      },
      error: () => {
        this.pricingLoading = false;
        this.pricingError = 'No pudimos obtener las modalidades de pago actuales. Intentá nuevamente más tarde.';
      }
    }));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  toggleFaq(id: string): void {
    this.openFaq = this.openFaq === id ? null : id;
  }

  isOpen(id: string): boolean {
    return this.openFaq === id;
  }

  markPriceTouched(): void {
    this.priceTouched = true;
  }

  invalidPrice(): boolean {
    return this.priceTouched && this.priceControl.invalid;
  }

  markCombinedTotalTouched(): void {
    this.combinedTotalTouched = true;
  }

  markCombinedCardTouched(): void {
    this.combinedCardTouched = true;
  }

  invalidCombinedTotal(): boolean {
    return this.combinedTotalTouched && this.combinedTotalControl.invalid;
  }

  invalidCombinedCard(): boolean {
    return this.combinedCardTouched
      && (this.combinedCardControl.invalid || this.combinedCardExceedsTotal());
  }

  combinedCardExceedsTotal(): boolean {
    const total = this.combinedTotalControl.value;
    const card = this.combinedCardControl.value;
    return typeof total === 'number' && typeof card === 'number' && card > total;
  }

  combinedBalanceLabel(): string {
    return this.combinedBalanceMethodControl.value === 'transfer' ? 'transferencia' : 'efectivo';
  }

  formatArs(value: number | null | undefined): string {
    return typeof value === 'number' && Number.isFinite(value)
      ? value.toLocaleString('es-AR', {
        style: 'currency',
        currency: 'ARS',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
      })
      : '—';
  }

  installmentDetail(option: CatalogFinancingOption): string {
    return option.installments && option.installmentAmount
      ? `${option.installments} cuotas de ${this.formatArs(option.installmentAmount)}`
      : '';
  }

  consultarWhatsapp(): void {
    this.whatsappService.openHomeConsultation();
  }

  private updateSimulation(): void {
    const basePrice = this.priceControl.value;
    this.simulation = this.paymentProfile && typeof basePrice === 'number'
      ? this.financingService.simulate(basePrice, this.paymentProfile)
      : null;
  }

  private updateCombinedSimulation(): void {
    const total = this.combinedTotalControl.value;
    const financed = this.combinedCardControl.value;
    this.combinedSimulation = this.paymentProfile
      && typeof total === 'number'
      && typeof financed === 'number'
      ? this.financingService.simulateCombined(
        total,
        financed,
        this.combinedBalanceMethodControl.value,
        this.paymentProfile
      )
      : null;
  }
}
