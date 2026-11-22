import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
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
  simulation: CatalogPaymentSimulation | null = null;
  readonly priceControl = new FormControl<number | null>(null, [
    Validators.required,
    Validators.min(1)
  ]);

  private paymentProfile: CatalogPaymentProfile | null = null;
  private readonly subscriptions = new Subscription();

  constructor(
    private nuevosPreciosService: NuevosPreciosService,
    private financingService: CatalogFinancingService,
    private whatsappService: CatalogWhatsappService
  ) {}

  ngOnInit(): void {
    this.subscriptions.add(this.priceControl.valueChanges.subscribe(() => this.updateSimulation()));
    this.subscriptions.add(this.nuevosPreciosService.getCatalogoPrincipal().subscribe({
      next: catalog => {
        this.paymentProfile = this.financingService.profileFromCatalog(catalog);
        this.pricingLoading = false;
        this.pricingError = this.paymentProfile
          ? ''
          : 'No pudimos obtener las modalidades de pago actuales. Intentá nuevamente más tarde.';
        this.updateSimulation();
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
}
