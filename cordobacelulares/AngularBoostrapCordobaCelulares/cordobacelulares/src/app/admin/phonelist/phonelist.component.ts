import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ModelService } from '../../services/model.service';
import { RouterLink } from '@angular/router';
import { ModelNewDto } from '../../models/ModelNewDto';

@Component({
  selector: 'app-phonelist',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './phonelist.component.html',
  styleUrl: './phonelist.component.css'
})
export class PhonelistComponent {
  phoneList: ModelNewDto[] = [];

  constructor(private modelService: ModelService) {}

  ngOnInit(): void {
    this.loadPhones();
  }

  loadPhones(): void {
    this.modelService.getAllBrands().subscribe(data => {
      this.phoneList = data;
    });
  }
}
