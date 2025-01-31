import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminphonelistComponent } from './adminphonelist.component';

describe('AdminphonelistComponent', () => {
  let component: AdminphonelistComponent;
  let fixture: ComponentFixture<AdminphonelistComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminphonelistComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminphonelistComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
