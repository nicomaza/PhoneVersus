import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';

interface AdminNavItem {
  label: string;
  path: string;
  icon: string;
}

@Component({
  selector: 'app-admin-config-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-config-layout.component.html',
  styleUrl: './admin-config-layout.component.css'
})
export class AdminConfigLayoutComponent implements OnInit, OnDestroy {
  readonly navItems: AdminNavItem[] = [
    { label: 'Cotizaciones', path: '/config/bombai/cotizaciones', icon: 'bi-currency-dollar' },
    { label: 'Credenciales', path: '/config/bombai/credenciales', icon: 'bi-key' },
    { label: 'Equipos', path: '/config/bombai/equipos', icon: 'bi-phone' },
    { label: 'Comparativa', path: '/config/bombai/comparativa', icon: 'bi-bar-chart-line' },
    { label: 'Actualizacion', path: '/config/bombai/actualizacion', icon: 'bi-arrow-repeat' }
  ];

  menuOpen = false;
  sectionTitle = 'Panel Bombai';
  sectionDescription = 'Configuracion operativa de catalogo y proveedores.';

  private routeSubscription?: Subscription;

  constructor(
    private router: Router,
    private activatedRoute: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.updateSectionCopy();
    this.routeSubscription = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => {
        this.menuOpen = false;
        this.updateSectionCopy();
      });
  }

  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
  }

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  closeMenu(): void {
    this.menuOpen = false;
  }

  private updateSectionCopy(): void {
    const activeRoute = this.deepestChild(this.activatedRoute);
    this.sectionTitle = this.readDataString(activeRoute, 'title', 'Panel Bombai');
    this.sectionDescription = this.readDataString(
      activeRoute,
      'description',
      'Configuracion operativa de catalogo y proveedores.'
    );
  }

  private deepestChild(route: ActivatedRoute): ActivatedRoute {
    let current = route;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return current;
  }

  private readDataString(route: ActivatedRoute, key: string, fallback: string): string {
    const value = route.snapshot.data[key];
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
    return fallback;
  }
}
