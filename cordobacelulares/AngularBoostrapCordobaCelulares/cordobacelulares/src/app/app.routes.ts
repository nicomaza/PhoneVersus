import { Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { FaqsComponent } from './faqs/faqs.component';
import { FeaturesComponent } from './features/features.component';
import { PhoneListComponent } from './phone-list/phone-list.component';
import { WarrantyComponent } from './warranty/warranty.component';
import { GlossaryComponent } from './glossary/glossary.component';
import { ComparativesComponent } from './comparatives/comparatives.component';
import { VersusComponent } from './versus/versus.component';


export const routes: Routes = [{
    path: 'home',
    title: 'Home',
    component: HomeComponent
},
{
    path: 'phonelist',
    component: PhoneListComponent
},
{
    path: 'phonelist/:brand',
    component: PhoneListComponent
},
{
    path: 'faqs',
    title: 'faqs',
    component: FaqsComponent
},

{
    path: 'phone/:id',
    component: FeaturesComponent
},
{
    path: 'comparatives',
    component: ComparativesComponent
},
{
    path: 'comparatives/:id',
    component: ComparativesComponent
},
{
    path: 'versus/:id1/:id2',
    component: VersusComponent
},
{
    path: 'warranty',
    component: WarrantyComponent
},
{
    path: 'glossary',
    component: GlossaryComponent
},
{
    path: '',
    redirectTo: 'home',
    pathMatch: 'full'
},
{
    path: '**',
    redirectTo: 'home'
}
];