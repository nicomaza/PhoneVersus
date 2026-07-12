import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminConfigHttpError, AdminConfigService } from '../services/admin-config.service';
import { PriceConfigurationRequest, PriceConfigurationResponse } from '../models/admin-config.models';

type PriceConfigurationField = keyof PriceConfigurationRequest;

@Component({
  selector: 'app-quotations-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './quotations-admin.component.html',
  styleUrl: './quotations-admin.component.css'
})
export class QuotationsAdminComponent implements OnInit {
  configuration: PriceConfigurationResponse | null = null;
  loading = false;
  saving = false;
  error = '';
  success = '';
  confirmDelete = false;

  readonly form = this.formBuilder.nonNullable.group({
    dolarBillete: [0, [Validators.required, Validators.min(0.01)]],
    usdt: [0, [Validators.required, Validators.min(0.01)]],
    transferenciaBancaria: [0, [Validators.required, Validators.min(0)]],
    tarjeta3Pagos: [0, [Validators.required, Validators.min(0)]],
    tarjeta6Pagos: [0, [Validators.required, Validators.min(0)]],
    tarjeta12Pagos: [0, [Validators.required, Validators.min(0)]]
  });

  constructor(
    private formBuilder: FormBuilder,
    private adminConfigService: AdminConfigService
  ) { }

  ngOnInit(): void {
    this.loadConfiguration();
  }

  loadConfiguration(): void {
    this.loading = true;
    this.error = '';
    this.adminConfigService.getPriceConfiguration().subscribe({
      next: configuration => {
        this.configuration = configuration;
        this.form.setValue({
          dolarBillete: this.toNumber(configuration.dolarBillete),
          usdt: this.toNumber(configuration.usdt),
          transferenciaBancaria: this.toNumber(configuration.transferenciaBancaria),
          tarjeta3Pagos: this.toNumber(configuration.tarjeta3Pagos),
          tarjeta6Pagos: this.toNumber(configuration.tarjeta6Pagos),
          tarjeta12Pagos: this.toNumber(configuration.tarjeta12Pagos)
        });
      },
      error: (error: Error) => {
        if (error instanceof AdminConfigHttpError && error.status === 404) {
          this.configuration = null;
          this.loading = false;
          this.form.reset({
            dolarBillete: 0,
            usdt: 0,
            transferenciaBancaria: 0,
            tarjeta3Pagos: 0,
            tarjeta6Pagos: 0,
            tarjeta12Pagos: 0
          });
          return;
        }
        this.error = error.message;
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  save(): void {
    if (this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.toRequest();
    this.saving = true;
    this.error = '';
    this.success = '';

    const operation = this.configuration
      ? this.adminConfigService.updatePriceConfiguration(request)
      : this.adminConfigService.createPriceConfiguration(request);

    operation.subscribe({
      next: configuration => {
        this.configuration = configuration;
        this.success = 'Cotizaciones guardadas.';
        this.form.markAsPristine();
      },
      error: (error: Error) => {
        this.error = error.message;
        this.saving = false;
      },
      complete: () => {
        this.saving = false;
      }
    });
  }

  askDelete(): void {
    this.confirmDelete = true;
  }

  cancelDelete(): void {
    this.confirmDelete = false;
  }

  deleteConfiguration(): void {
    if (this.saving) {
      return;
    }
    this.saving = true;
    this.error = '';
    this.success = '';
    this.adminConfigService.deletePriceConfiguration().subscribe({
      next: () => {
        this.configuration = null;
        this.confirmDelete = false;
        this.success = 'Configuracion eliminada.';
        this.form.reset({
          dolarBillete: 0,
          usdt: 0,
          transferenciaBancaria: 0,
          tarjeta3Pagos: 0,
          tarjeta6Pagos: 0,
          tarjeta12Pagos: 0
        });
      },
      error: (error: Error) => {
        this.error = error.message;
        this.saving = false;
      },
      complete: () => {
        this.saving = false;
      }
    });
  }

  controlInvalid(field: PriceConfigurationField): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.dirty || control.touched);
  }

  formatMoney(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '-';
    }
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 2
    }).format(value);
  }

  formatPercent(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '-';
    }
    return `${value}%`;
  }

  formatDate(value: string | null | undefined): string {
    if (!value) {
      return '-';
    }
    return new Date(value).toLocaleString('es-AR');
  }

  private toRequest(): PriceConfigurationRequest {
    const raw = this.form.getRawValue();
    return {
      dolarBillete: raw.dolarBillete,
      usdt: raw.usdt,
      transferenciaBancaria: raw.transferenciaBancaria,
      tarjeta3Pagos: raw.tarjeta3Pagos,
      tarjeta6Pagos: raw.tarjeta6Pagos,
      tarjeta12Pagos: raw.tarjeta12Pagos
    };
  }

  private toNumber(value: number | null): number {
    if (value === null || Number.isNaN(value)) {
      return 0;
    }
    return value;
  }
}
