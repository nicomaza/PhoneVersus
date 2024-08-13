import { Component, Input, OnInit } from '@angular/core';
import { Phone } from '../models/phone';
import { PhonesService } from '../services/phones.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-versus',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './versus.component.html',
  styleUrl: './versus.component.css'
})
export class VersusComponent implements OnInit {
  @Input('id1') idphoneLeft!: string;
  @Input('id2') idphoneRight!: string;

  phoneleft!: Phone;
  phoneright!: Phone;

  constructor(private phoneService: PhonesService) { }
  ngOnInit(): void {
    const idleft = parseInt(this.idphoneLeft);
    this.phoneService.getPhoneById(idleft).subscribe(
      (data) => { this.phoneleft = data },
      (error) => { console.log(error) })


    const idright = parseInt(this.idphoneRight);
    this.phoneService.getPhoneById(idright).subscribe(
      (data) => { this.phoneright = data },
      (error) => { console.log(error) })
  }
}
