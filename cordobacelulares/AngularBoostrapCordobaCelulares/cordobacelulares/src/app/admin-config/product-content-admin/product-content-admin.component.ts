import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, debounceTime, distinctUntilChanged, finalize } from 'rxjs';
import { ProductContentListItem } from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

@Component({
  selector: 'app-product-content-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './product-content-admin.component.html',
  styleUrl: './product-content-admin.component.css'
})
export class ProductContentAdminComponent implements OnInit, OnDestroy {
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly states = [
    { value: 'ALL', label: 'Todos' },
    { value: 'NONE', label: 'Sin contenido' },
    { value: 'DRAFT', label: 'Borrador' },
    { value: 'PUBLISHED', label: 'Publicado' }
  ];

  products: ProductContentListItem[] = [];
  brands: string[] = [];
  brand = '';
  state = 'ALL';
  page = 1;
  readonly limit = 25;
  total = 0;
  totalPages = 0;
  loading = false;
  error = '';

  private readonly subscriptions = new Subscription();

  constructor(private service: AdminConfigService) {}

  ngOnInit(): void {
    this.subscriptions.add(
      this.searchControl.valueChanges
        .pipe(debounceTime(250), distinctUntilChanged())
        .subscribe(() => {
          this.page = 1;
          this.load();
        })
    );
    this.load();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.subscriptions.add(
      this.service.getProductContents({
        text: this.searchControl.value.trim(),
        brand: this.brand,
        state: this.state,
        page: this.page,
        limit: this.limit
      }).pipe(finalize(() => this.loading = false)).subscribe({
        next: response => {
          this.products = response.data;
          this.brands = response.brands;
          this.total = response.total;
          this.totalPages = response.totalPages;
        },
        error: (error: Error) => this.error = error.message
      })
    );
  }

  changeBrand(value: string): void {
    this.brand = value;
    this.page = 1;
    this.load();
  }

  changeState(value: string): void {
    this.state = value;
    this.page = 1;
    this.load();
  }

  previousPage(): void {
    if (this.page <= 1) return;
    this.page--;
    this.load();
  }

  nextPage(): void {
    if (this.page >= this.totalPages) return;
    this.page++;
    this.load();
  }

  statusLabel(item: ProductContentListItem): string {
    if (item.status === 'PUBLISHED') return item.hasUnpublishedChanges ? 'Publicado · cambios pendientes' : 'Publicado';
    if (item.status === 'DRAFT') return 'Borrador';
    return 'Sin contenido';
  }

  statusClass(item: ProductContentListItem): string {
    if (item.status === 'PUBLISHED') return 'status-published';
    if (item.status === 'DRAFT') return 'status-draft';
    return 'status-empty';
  }

  formatDate(value: string | null): string {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('es-AR');
  }
}
