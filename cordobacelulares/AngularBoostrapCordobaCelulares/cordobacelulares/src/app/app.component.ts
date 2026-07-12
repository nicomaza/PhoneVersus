import { Component } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { HeaderComponent } from "./header/header.component";
import { FooterComponent } from "./footer/footer.component";
import { HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, FooterComponent, HttpClientModule, CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'cordobacelulares';


  showHeaderFooter = true;

  constructor(private router: Router) {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => {
        this.showHeaderFooter = !this.routeHidesPublicLayout(this.router.routerState.snapshot.root);
      });
  }

  private routeHidesPublicLayout(route: ActivatedRouteSnapshot): boolean {
    let current: ActivatedRouteSnapshot | null = route;
    while (current) {
      if (current.data['hidePublicLayout'] === true) {
        return true;
      }
      current = current.firstChild;
    }
    return false;
  }
}
