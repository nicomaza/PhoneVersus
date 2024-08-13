import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ComparativesComponent } from './comparatives.component';

describe('ComparativesComponent', () => {
  let component: ComparativesComponent;
  let fixture: ComponentFixture<ComparativesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComparativesComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ComparativesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
