import { Component, OnInit } from '@angular/core';
import { ModelService } from '../../services/model.service';
import { ModelNewDto } from '../../models/ModelNewDto';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { PhonesService } from '../../services/phones.service';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-adminphonelist',
  standalone: true,
  imports: [CommonModule, RouterLink, NgSelectModule],
  templateUrl: './adminphonelist.component.html',
  styleUrl: './adminphonelist.component.css'
})
export class AdminphonelistComponent implements OnInit {


  phoneList: ModelNewDto[] = [];
  filteredList: ModelNewDto[] = [];

  constructor(private modelservice: ModelService, private phoneservice: PhonesService) { }

  ngOnInit(): void {
    this.loadPhones();
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

    console.log('🔎 Modelo seleccionado:', selectedId);

    this.filteredList = this.phoneList.filter(phone => phone.idModel === selectedId.idModel);
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
