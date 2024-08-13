import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BrandService } from '../services/brand.service';
import { Brand } from '../models/brand';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css'
})
export class HeaderComponent implements OnInit {
  @ViewChild('navbarCollapse', { static: false }) navbarCollapse: any;
  marcas: string[] = ["Samsung", "Iphone", "Morotola"];
  Brand: Brand[] = [];
  constructor(private brandService: BrandService) {
  }
  ngOnInit(): void {
    this.brandService.getAllBrands().subscribe((data: Brand[]) => {
      this.Brand = data;
    },
      (error) => {
        console.error('Error fetching phones', error);
      })
  }


  toggleNavbar() {
    if (this.navbarCollapse && this.navbarCollapse.nativeElement.classList.contains('show')) {
      this.navbarCollapse.nativeElement.classList.remove('show');
    }
  }

}
