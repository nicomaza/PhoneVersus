import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Phone } from '../models/phone';
import { PhonesService } from '../services/phones.service';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PhoneItemComponent } from "../phone-item/phone-item.component";
import { FilterphonePipe } from '../pipes/filterphone.pipe';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';

@Component({
  selector: 'app-phone-list',
  standalone: true,
  imports: [CommonModule, RouterModule, PhoneItemComponent, FilterphonePipe, FormsModule, HttpClientModule],
  templateUrl: './phone-list.component.html',
  styleUrl: './phone-list.component.css'
})
export class PhoneListComponent implements OnInit, OnChanges {

  @Input('brand') brandName!: string;
  searchText = '';
  phones: Phone[] = [];
  displayPhones: Phone[] = [];
  page: number = 0;
  filteredPhones: Phone[] = [];
  quantitypages: number = 0;
  currentPage: number = 1;
  itemsPerPage: number = 6;

  constructor(private phoneService: PhonesService) { }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['brandName']) {
      this.phones = [];
      this.loadPhones();
    }

  }ngOnInit(): void {
    this.loadPhones();
  }

  loadPhones(): void {
    if (this.brandName === "todos") {
      this.phoneService.getAllPhones().subscribe(
        (data: Phone[]) => {
          this.phones = data;
          this.filterPhones();
        },
        (error) => {
          console.error('Error fetching phones', error);
        }
      );
    } else {
      this.phoneService.getPhonesByBrand(this.brandName).subscribe(
        (data: Phone[]) => {
          this.phones = data;
          this.filterPhones();
        },
        (error) => {
          console.error('Error fetching phones', error);
        }
      );
    }
  }

  filterPhones(): void {
    console.log("phones",this.phones)
    console.log(this.searchText)
    this.filteredPhones = this.phones.filter(phone =>
      phone.brand.toLowerCase().includes(this.searchText.toLowerCase()) || 
      phone.model.toLowerCase().includes(this.searchText.toLowerCase())
    );
    console.log(this.filteredPhones)
    this.quantitypages = Math.ceil(this.filteredPhones.length / this.itemsPerPage);
    this.updatePage();
  }

  updatePage(): void {
    const startIndex = (this.currentPage - 1) * this.itemsPerPage;
    const endIndex = startIndex + this.itemsPerPage;
    this.displayPhones = this.filteredPhones.slice(startIndex, endIndex);
  }

  changePage(page: number): void {
    this.currentPage = page;
    this.updatePage();
  }

  previusPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
      this.updatePage();
    }
  }

  nextPage(): void {
    if (this.currentPage < this.quantitypages) {
      this.currentPage++;
      this.updatePage();
    }
  }

  resetPage(): void {
    this.currentPage = 1;
    this.filterPhones();
  }
}