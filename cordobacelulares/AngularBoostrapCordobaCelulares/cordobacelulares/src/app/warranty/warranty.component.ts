import { Component } from '@angular/core';
import { HomeComponent } from '../home/home.component';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-warranty',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './warranty.component.html',
  styleUrl: './warranty.component.css'
})
export class WarrantyComponent {

}
