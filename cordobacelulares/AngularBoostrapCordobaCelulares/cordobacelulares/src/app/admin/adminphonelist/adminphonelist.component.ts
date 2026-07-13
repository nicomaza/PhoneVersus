import { Component, OnDestroy, OnInit } from '@angular/core';
import { ModelService } from '../../services/model.service';
import { ModelNewDto } from '../../models/ModelNewDto';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { PhonesService } from '../../services/phones.service';
import Swal from 'sweetalert2';
import { Subscription, interval } from 'rxjs';
import { CatalogCacheDateValue, CatalogCacheService, CatalogCacheStatusResponse } from '../../services/catalog-cache.service';

@Component({
  selector: 'app-adminphonelist',
  standalone: true,
  imports: [CommonModule, RouterLink, NgSelectModule],
  templateUrl: './adminphonelist.component.html',
  styleUrl: './adminphonelist.component.css'
})
export class AdminphonelistComponent implements OnInit, OnDestroy {


  phoneList: ModelNewDto[] = [];
  filteredList: ModelNewDto[] = [];
  cacheStatus: CatalogCacheStatusResponse | null = null;
  cacheStatusLoading = false;
  cacheRefreshSubmitting = false;
  private cachePolling?: Subscription;

  constructor(
    private modelservice: ModelService,
    private phoneservice: PhonesService,
    private catalogCacheService: CatalogCacheService
  ) { }

  ngOnInit(): void {
    this.loadPhones();
    this.loadCatalogCacheStatus();
  }

  ngOnDestroy(): void {
    this.stopCatalogCachePolling();
  }

  loadPhones(): void {
    this.modelservice.getAllBrands().subscribe(data => {
      this.phoneList = data;
      this.filteredList = data
    });
  }

  search(selectedId: ModelNewDto) {
    if (!selectedId) {
      this.filteredList = this.phoneList;
      return;
    }


    this.filteredList = this.phoneList.filter(phone => phone.idModel === selectedId.idModel);
  }

  loadCatalogCacheStatus(): void {
    this.cacheStatusLoading = true;
    this.catalogCacheService.getStatus().subscribe({
      next: status => {
        this.cacheStatus = status;
        if (status.refreshing) {
          this.startCatalogCachePolling();
        } else {
          this.stopCatalogCachePolling();
        }
      },
      complete: () => {
        this.cacheStatusLoading = false;
      },
      error: () => {
        this.cacheStatusLoading = false;
      }
    });
  }

  refreshCatalogCache(): void {
    if (this.cacheRefreshSubmitting || this.cacheStatus?.refreshing) {
      return;
    }
    this.cacheRefreshSubmitting = true;
    this.catalogCacheService.refresh().subscribe({
      next: response => {
        this.cacheStatus = response.status;
        if (response.blockedByCooldown) {
          Swal.fire('Actualizacion bloqueada', 'Tienda Porte pidio esperar antes de un nuevo intento.', 'warning');
          return;
        }
        if (response.alreadyRunning) {
          Swal.fire('Actualizacion en curso', 'Ya existe una actualizacion del catalogo ejecutandose.', 'info');
        } else if (response.refreshStarted) {
          Swal.fire('Actualizacion iniciada', 'El catalogo se esta actualizando en segundo plano.', 'success');
        }
        if (response.status.refreshing) {
          this.startCatalogCachePolling();
        }
      },
      error: () => {
        this.cacheRefreshSubmitting = false;
        Swal.fire('Error', 'No se pudo solicitar la actualizacion del catalogo.', 'error');
      },
      complete: () => {
        this.cacheRefreshSubmitting = false;
      }
    });
  }

  cacheStateLabel(): string {
    if (!this.cacheStatus?.initialized) {
      return 'Sin catalogo';
    }
    if (this.cacheStatus.refreshing) {
      return 'Actualizando';
    }
    if (this.cacheStatus.stale) {
      return 'Datos desactualizados';
    }
    return 'Actualizado';
  }

  snapshotAgeLabel(): string {
    if (!this.cacheStatus?.lastSuccessfulRefreshAt) {
      return '-';
    }
    const lastSuccessDate = this.parseCatalogCacheDate(this.cacheStatus.lastSuccessfulRefreshAt);
    if (!lastSuccessDate) {
      return '-';
    }
    const lastSuccess = lastSuccessDate.getTime();
    const elapsedSeconds = Math.max(0, Math.floor((Date.now() - lastSuccess) / 1000));
    if (elapsedSeconds < 60) {
      return `${elapsedSeconds}s`;
    }
    return `${Math.floor(elapsedSeconds / 60)}m ${elapsedSeconds % 60}s`;
  }

  formatDate(value?: CatalogCacheDateValue): string {
    const date = this.parseCatalogCacheDate(value);
    if (!date) {
      return '-';
    }
    return date.toLocaleString('es-AR');
  }

  private parseCatalogCacheDate(value?: CatalogCacheDateValue): Date | null {
    if (value === null || value === undefined) {
      return null;
    }
    if (typeof value === 'number') {
      const millis = Math.abs(value) < 1_000_000_000_000 ? value * 1000 : value;
      const date = new Date(millis);
      return Number.isNaN(date.getTime()) ? null : date;
    }
    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    if (/^-?\d+(\.\d+)?$/.test(trimmed)) {
      return this.parseCatalogCacheDate(Number(trimmed));
    }
    const date = new Date(trimmed);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private startCatalogCachePolling(): void {
    if (this.cachePolling) {
      return;
    }
    this.cachePolling = interval(2000).subscribe(() => this.loadCatalogCacheStatus());
  }

  private stopCatalogCachePolling(): void {
    this.cachePolling?.unsubscribe();
    this.cachePolling = undefined;
  }

  deletephone(id: number) {

    Swal.fire({
      title: "Are you sure?",
      text: "You won't be able to revert this!",
      icon: "warning",
      showCancelButton: true,
      confirmButtonColor: "#3085d6",
      cancelButtonColor: "#d33",
      confirmButtonText: "Yes, delete it!"
    }).then((result) => {
      if (result.isConfirmed) {
        this.phoneservice.deletePhoneById(id.toString());
        this.loadPhones();
        Swal.fire({
          title: "Deleted!",
          text: "Your file has been deleted.",
          icon: "success"
        });
      }
    });
  }
}
