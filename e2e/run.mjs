// Verificación de extremo a extremo con un navegador real contra el stack en marcha.
// Uso: BASE_URL=http://localhost:8080 SHOTS=/ruta/capturas node run.mjs
import { chromium } from 'playwright';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const SHOTS = process.env.SHOTS ?? '';
if (SHOTS) mkdirSync(SHOTS, { recursive: true });

const results = [];
let failed = 0;
async function step(name, fn) {
  try {
    await fn();
    results.push(`✔ ${name}`);
  } catch (e) {
    failed++;
    results.push(`✘ ${name}\n    ${String(e.message).split('\n').slice(0, 4).join('\n    ')}`);
  }
}
const expect = (cond, msg) => { if (!cond) throw new Error(msg); };

const stamp = Date.now();
const emailA = `e2e-a-${stamp}@example.com`;
const emailB = `e2e-b-${stamp}@example.com`;
const PASSWORD = 'e2e-correct-horse-battery';

const browser = await chromium.launch();
const ctxA = await browser.newContext({ viewport: { width: 1280, height: 800 }, acceptDownloads: true });
const page = await ctxA.newPage();
const consoleErrors = [];
// Los 401 son esperados: /auth/me sin sesión al arrancar y credenciales erróneas.
page.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('status of 401')) consoleErrors.push(m.text()); });
page.on('pageerror', (e) => consoleErrors.push(String(e)));
const shot = async (name) => { if (SHOTS) await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true }); };

let offerUrl = '';

await step('1. Registro desde la interfaz y acceso al panel vacío', async () => {
  await page.goto(`${BASE}/registro`);
  await shot('01-registro');
  await page.getByLabel('Nombre').fill('Ana E2E');
  await page.getByLabel('Email').fill(emailA);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await page.waitForURL('**/dashboard');
  await page.getByText('Empieza guardando tu primera oferta').waitFor();
  await shot('02-dashboard-vacio');
});

await step('2. Crear oferta con validación y verla en el detalle', async () => {
  await page.goto(`${BASE}/ofertas/nueva`);
  await page.getByRole('button', { name: 'Crear oferta' }).click();
  await page.getByText('Este campo es obligatorio.').first().waitFor();
  await page.getByLabel('Puesto *').fill('Java Backend Developer');
  await page.getByLabel('Empresa *').fill('Acme Corp');
  await page.getByLabel('URL de la oferta').fill('https://acme.example/jobs/42');
  await page.getByLabel('Ubicación').fill('Madrid');
  await page.getByLabel('Modalidad').selectOption('REMOTE');
  await page.getByLabel('Salario mínimo (bruto anual)').fill('40000');
  await page.getByLabel('Salario máximo').fill('55000');
  await page.getByLabel('Moneda').fill('eur');
  await page.getByLabel('Experiencia mínima (años)').fill('3');
  await page.locator('#skills').fill('Java, Spring Boot, Kafka');
  await page.locator('#skills').press('Enter');
  await page.getByLabel('Descripción').fill('Desarrollo de APIs REST.');
  await shot('03-formulario-oferta');
  await page.getByRole('button', { name: 'Crear oferta' }).click();
  await page.waitForURL(/\/ofertas\/[0-9a-f-]{36}$/);
  offerUrl = page.url();
  await page.getByRole('heading', { name: 'Java Backend Developer' }).waitFor();
  await page.getByText('40.000 – 55.000 EUR').waitFor();
  await page.getByText('Información insuficiente', { exact: true }).waitFor(); // aún sin perfil
});

await step('3. Cambiar estado con nota y ver el historial', async () => {
  await page.getByRole('button', { name: 'Cambiar estado' }).click();
  await page.getByLabel('Nuevo estado').selectOption('APPLIED');
  await page.getByLabel('Nota (opcional)').fill('Enviado por LinkedIn');
  await page.getByRole('button', { name: 'Guardar', exact: true }).click();
  await page.getByText('Estado actualizado').waitFor();
  await page.getByText('Enviado por LinkedIn').waitFor();
  await page.getByText(/Enviada el/).first().waitFor();
  await shot('04-detalle');
});

await step('4. Editar la oferta', async () => {
  await page.getByRole('link', { name: 'Editar' }).click();
  await page.getByLabel('Puesto *').fill('Senior Java Backend Developer');
  await page.getByRole('button', { name: 'Guardar cambios' }).click();
  await page.getByRole('heading', { name: 'Senior Java Backend Developer' }).waitFor();
});

await step('5. Marcar favorita, verla en /favoritas y filtrar', async () => {
  await page.getByRole('button', { name: 'Favorita', pressed: false }).click();
  await page.getByRole('button', { name: 'Favorita', pressed: true }).waitFor();
  await page.goto(`${BASE}/favoritas`);
  await page.getByRole('link', { name: 'Senior Java Backend Developer' }).waitFor();
  await page.goto(`${BASE}/ofertas`);
  await page.getByLabel('Solo favoritas').check();
  await page.waitForURL(/favorite=true/);
  await page.getByRole('link', { name: 'Senior Java Backend Developer' }).waitFor();
});

await step('6. Búsqueda y filtros combinables (backend)', async () => {
  await page.goto(`${BASE}/ofertas`);
  await page.getByLabel('Buscar').fill('kafka');
  await page.getByRole('link', { name: 'Senior Java Backend Developer' }).waitFor();
  await page.getByLabel('Buscar').fill('zzz-no-existe');
  await page.getByText('Sin resultados').waitFor();
  await page.getByRole('button', { name: 'Limpiar filtros' }).first().click();
  await page.getByRole('link', { name: 'Senior Java Backend Developer' }).waitFor();
  await page.getByLabel('Estado', { exact: true }).selectOption('REJECTED');
  await page.getByText('Sin resultados').waitFor();
  await page.getByRole('button', { name: 'Limpiar filtros' }).first().click();
  await shot('05-listado');
});

await step('7. Perfil profesional y cálculo de compatibilidad', async () => {
  await page.goto(`${BASE}/perfil`);
  await page.getByLabel('Años de experiencia').fill('5');
  await page.locator('#skills').fill('java, spring boot');
  await page.locator('#skills').press('Enter');
  await page.locator('#roles').fill('Java Backend Developer');
  await page.locator('#roles').press('Enter');
  await page.getByLabel('Modalidad preferida').selectOption('REMOTE');
  await page.getByRole('button', { name: 'Añadir idioma' }).click();
  await page.locator('#lang-0').fill('Inglés');
  await shot('06-perfil');
  await page.getByRole('button', { name: 'Guardar perfil' }).click();
  await page.getByText('Perfil guardado').waitFor();
  await page.goto(offerUrl);
  // skills 2/3 (50*.667) + exp (15) + puesto (15) + modalidad (10) = 73.3 / 90 => 81
  await page.getByText('81/100').waitFor();
  await page.getByText('Competencia requerida no registrada en tu perfil: Kafka').waitFor();
  await shot('07-detalle-compatibilidad');
});

await step('8. El panel y las estadísticas usan datos reales', async () => {
  await page.goto(`${BASE}/dashboard`);
  await page.locator('.kpi', { hasText: 'Ofertas guardadas' }).getByText('1', { exact: true }).waitFor();
  await page.locator('.kpi', { hasText: 'Candidaturas enviadas' }).getByText('1', { exact: true }).waitFor();
  await page.getByText('Mejor compatibilidad').waitFor();
  await shot('08-dashboard');
  await page.goto(`${BASE}/estadisticas`);
  await page.getByText('poco fiable').waitFor();
  await shot('09-estadisticas');
});

await step('9. Exportar e importar CSV', async () => {
  await page.goto(`${BASE}/ofertas`);
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Exportar CSV' }).click()]);
  const path = await download.path();
  const csv = readFileSync(path, 'utf8');
  expect(csv.includes('Senior Java Backend Developer'), 'el CSV exportado no contiene la oferta');
  writeFileSync(`${path}.import.csv`, 'title,company,skills,status\nQA Engineer,Beta,Cypress;Playwright,SAVED\nData Analyst,Gamma,SQL,APPLIED\n');
  await page.getByRole('button', { name: 'Importar CSV' }).click();
  await page.getByLabel('Archivo CSV (UTF-8)').setInputFiles(`${path}.import.csv`);
  await page.getByText('2 válidas').waitFor();
  await shot('10-importar');
  await page.getByRole('button', { name: 'Importar 2 ofertas' }).click();
  await page.getByText('2 ofertas importadas').waitFor();
  await page.getByRole('link', { name: 'QA Engineer' }).waitFor();
});

await step('10. Eliminar una oferta pide confirmación', async () => {
  await page.getByRole('link', { name: 'QA Engineer' }).click();
  const main = page.locator('#main');
  await main.getByRole('button', { name: 'Eliminar' }).click();
  await page.getByRole('heading', { name: 'Eliminar oferta' }).waitFor();
  await page.getByRole('button', { name: 'Cancelar' }).click();
  await page.getByRole('heading', { name: 'QA Engineer' }).waitFor();
  await main.getByRole('button', { name: 'Eliminar' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Eliminar' }).click();
  await page.waitForURL('**/ofertas');
  await page.getByText('Oferta eliminada').waitFor();
});

await step('11. Cerrar sesión protege las rutas y se puede volver a entrar', async () => {
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await page.waitForURL('**/login');
  await page.goto(`${BASE}/ofertas`);
  await page.waitForURL(/\/login\?returnUrl/);
  await page.getByLabel('Email').fill(emailA);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.waitForURL('**/ofertas');
  await page.getByRole('link', { name: 'Senior Java Backend Developer' }).waitFor();
});

await step('12. Otro usuario no puede ver ni abrir datos ajenos', async () => {
  const ctxB = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const b = await ctxB.newPage();
  await b.goto(`${BASE}/registro`);
  await b.getByLabel('Nombre').fill('Bruno E2E');
  await b.getByLabel('Email').fill(emailB);
  await b.getByLabel('Contraseña').fill(PASSWORD);
  await b.getByRole('button', { name: 'Crear cuenta' }).click();
  await b.waitForURL('**/dashboard');
  await b.goto(offerUrl);
  await b.getByText('Oferta no encontrada.').waitFor();
  await b.goto(`${BASE}/ofertas`);
  await b.getByText('Todavía no has guardado ninguna oferta').waitFor();
  const status = await b.evaluate(async (u) => (await fetch(u.replace(location.origin, ''))).status, offerUrl.replace('/ofertas/', '/api/v1/offers/'));
  expect(status === 404, `esperaba 404 al pedir la API de otra persona, recibí ${status}`);
  await ctxB.close();
});

await step('13. Cambio de contraseña', async () => {
  await page.goto(`${BASE}/cuenta`);
  await page.getByLabel('Contraseña actual').fill(PASSWORD);
  await page.getByLabel('Nueva contraseña', { exact: true }).fill('another-long-password-1');
  await page.getByLabel('Repite la nueva contraseña').fill('another-long-password-1');
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
  await page.getByText('Contraseña actualizada').waitFor();
});

await step('14. Diseño responsive (móvil): menú desplegable y sin desbordamiento horizontal', async () => {
  await page.setViewportSize({ width: 390, height: 800 });
  await page.goto(`${BASE}/ofertas`);
  await page.getByRole('link', { name: 'Senior Java Backend Developer' }).waitFor();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow <= 1, `desbordamiento horizontal de ${overflow}px en /ofertas`);
  await shot('11-movil-listado');
  await page.getByRole('button', { name: 'Abrir menú de navegación' }).click();
  await page.getByRole('link', { name: 'Estadísticas' }).waitFor();
  await shot('12-movil-menu');
  await page.getByRole('link', { name: 'Panel' }).click();
  await page.getByRole('heading', { name: /Hola/ }).waitFor();
  const overflow2 = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow2 <= 1, `desbordamiento horizontal de ${overflow2}px en /dashboard`);
  await shot('13-movil-dashboard');
});

await step('15. Sin errores de consola del navegador (incluida CSP)', async () => {
  expect(consoleErrors.length === 0, `errores de consola: ${consoleErrors.slice(0, 3).join(' | ')}`);
});

await browser.close();
console.log('\n' + results.join('\n'));
console.log(`\n${results.length - failed}/${results.length} pasos correctos`);
process.exit(failed ? 1 : 0);
