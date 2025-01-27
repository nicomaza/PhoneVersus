import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewphoneComponent } from './newphone.component';

describe('NewphoneComponent', () => {
  let component: NewphoneComponent;
  let fixture: ComponentFixture<NewphoneComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewphoneComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(NewphoneComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
