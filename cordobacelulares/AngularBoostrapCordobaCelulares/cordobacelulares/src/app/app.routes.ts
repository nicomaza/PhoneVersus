import { Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { FaqsComponent } from './faqs/faqs.component';
import { FeaturesComponent } from './features/features.component';
import { PhoneListComponent } from './phone-list/phone-list.component';
import { WarrantyComponent } from './warranty/warranty.component';
import { GlossaryComponent } from './glossary/glossary.component';
import { ComparativesComponent } from './comparatives/comparatives.component';
import { VersusComponent } from './versus/versus.component';
import { LoginComponent } from './admin/login/login.component';
import { NewphoneComponent } from './admin/newphone/newphone.component';
import { AdminphonelistComponent } from './admin/adminphonelist/adminphonelist.component';
import { NuevosPreciosComponent } from './list-prices/nuevos-precios/nuevos-precios.component';
import { AdminConfigLayoutComponent } from './admin-config/admin-config-layout/admin-config-layout.component';
import { CredentialsAdminComponent } from './admin-config/credentials-admin/credentials-admin.component';
import { QuotationsAdminComponent } from './admin-config/quotations-admin/quotations-admin.component';
import { TiendaPorteProductsAdminComponent } from './admin-config/tienda-porte-products-admin/tienda-porte-products-admin.component';
import { CatalogCacheAdminComponent } from './admin-config/catalog-cache-admin/catalog-cache-admin.component';


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
    path: 'adminhorse',
    data: { hidePublicLayout: true },
    component: LoginComponent
},

{
    path: 'newphone',
    component: NewphoneComponent
},
{
    path: 'newphone/edit/:idPhone',
    component: NewphoneComponent
},
{
    path: 'adminphonelist',
    component: AdminphonelistComponent
},
{
    path: 'config/bombai',
    component: AdminConfigLayoutComponent,
    data: { hidePublicLayout: true },
    // TODO: proteger /config/bombai y los endpoints administrativos mediante Nginx Basic Auth.
    children: [
        {
            path: '',
            redirectTo: 'cotizaciones',
            pathMatch: 'full'
        },
        {
            path: 'credenciales',
            component: CredentialsAdminComponent,
            data: {
                title: 'Credenciales',
                description: 'Gestion de credenciales usadas para autenticar contra Tienda Porte.'
            }
        },
        {
            path: 'cotizaciones',
            component: QuotationsAdminComponent,
            data: {
                title: 'Cotizaciones',
                description: 'Cotizacion dolar, USDT y porcentajes de recargo usados por el catalogo.'
            }
        },
        {
            path: 'equipos',
            component: TiendaPorteProductsAdminComponent,
            data: {
                title: 'Equipos Tienda Porte',
                description: 'Consulta paginada del snapshot cacheado, sin llamadas nuevas al proveedor.'
            }
        },
        {
            path: 'actualizacion',
            component: CatalogCacheAdminComponent,
            data: {
                title: 'Actualizacion',
                description: 'Estado y refresh manual del cache centralizado de catalogo.'
            }
        }
    ]
},

{
    path: 'listapreciosactual',
    component: NuevosPreciosComponent
},
{
    path: 'nuevosprecios',
    component: NuevosPreciosComponent
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
