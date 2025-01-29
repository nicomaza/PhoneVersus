import { TestBed } from '@angular/core/testing';

import { BoxcontentService } from './boxcontent.service';

describe('BoxcontentService', () => {
  let service: BoxcontentService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(BoxcontentService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
