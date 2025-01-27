import { Component } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { HeaderComponent } from "./header/header.component";
import { FooterComponent } from "./footer/footer.component";
import { HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';

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
    this.router.events.subscribe(() => {
      // Ocultar el header y footer si la ruta es '/login'
      this.showHeaderFooter = !this.router.url.includes('/adminhorse');
    });
  }
}
