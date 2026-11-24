import { useEffect, useState } from 'react';
import Mensaje from '../../components/Mensaje';
import { useToast } from '../../context/ToastContext';
import { editarPlanAdmin, listarPlanesAdmin } from '../../services/admin';
import { mensajeDeError } from '../../services/api';
import { BOTON, TARJETA, inputPanel } from '../../utils/panel';

const BENEFICIOS = [
  ['permitePersonalizar', 'Logo, color propio y sin el sello de Vendely'],
  ['permitePlantillas', 'Elegir plantilla Moderna o Boutique'],
  ['permiteMasMetodosPago', 'Más métodos de pago además de PayPal'],
  ['permiteEnvioInternacional', 'Envíos internacionales'],
];

/**
 * "Planes" del superadmin: precio, límite de productos, comisión y beneficios de cada plan.
 * Cambiar el precio no toca lo ya pagado (cada pago guarda su monto); se cobra desde el próximo pago.
 */
export default function AdminPlanes() {
  const [estado, setEstado] = useState({ cargando: true, planes: [], error: '' });

  useEffect(() => {
    let vigente = true;
    listarPlanesAdmin()
      .then((planes) => vigente && setEstado({ cargando: false, planes, error: '' }))
      .catch((e) => vigente && setEstado({ cargando: false, planes: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, []);

  return (
    <section className="space-y-4">
      <div>
        <h1 className="text-xl font-bold text-[#101010]">Planes</h1>
        <p className="text-[13px] text-[#616161]">
          Lo que cuesta cada plan y lo que incluye. Los cambios se aplican desde el próximo pago; lo ya pagado no cambia.
        </p>
      </div>
      {estado.error && <Mensaje tipo="error" texto={estado.error} />}
      {estado.cargando && <p className="text-[13px] text-[#616161]">Cargando planes...</p>}
      <div className="grid gap-4 lg:grid-cols-3">
        {estado.planes.map((p) => (
          <FormularioPlan key={p.idPlan} plan={p} />
        ))}
      </div>
    </section>
  );
}

function FormularioPlan({ plan }) {
  const { avisar } = useToast();
  const gratis = plan.idPlan === 1;
  const [v, setV] = useState({
    precioUsd: String(plan.precioUsd),
    ilimitado: plan.maxProductos == null,
    maxProductos: plan.maxProductos == null ? '' : String(plan.maxProductos),
    comision: String(plan.comision),
    permitePersonalizar: plan.permitePersonalizar,
    permitePlantillas: plan.permitePlantillas,
    permiteMasMetodosPago: plan.permiteMasMetodosPago,
    permiteEnvioInternacional: plan.permiteEnvioInternacional,
    maxEmpleados: String(plan.maxEmpleados ?? 0),
  });
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  const cambiar = (campo, valor) => {
    setV((x) => ({ ...x, [campo]: valor }));
    setErrores((e) => ({ ...e, [campo]: undefined }));
  };

  async function guardar(e) {
    e.preventDefault();
    const er = {};
    if (!/^\d{1,6}(\.\d{1,2})?$/.test(v.precioUsd.trim())) er.precioUsd = 'Monto en dólares, por ejemplo 15 o 15.90';
    else if (gratis && Number(v.precioUsd) !== 0) er.precioUsd = 'El plan Gratis siempre cuesta 0';
    else if (!gratis && Number(v.precioUsd) <= 0) er.precioUsd = 'Un plan pagado debe costar más de 0';
    if (!v.ilimitado && !/^[1-9]\d{0,6}$/.test(v.maxProductos.trim())) er.maxProductos = 'Número entero, 1 o más';
    if (!/^\d{1,3}(\.\d{1,2})?$/.test(v.comision.trim()) || Number(v.comision) > 100) er.comision = 'De 0 a 100, por ejemplo 2.5';
    if (!/^\d{1,4}$/.test(v.maxEmpleados.trim()) || Number(v.maxEmpleados) > 1000) er.maxEmpleados = 'De 0 a 1000 (0 = solo el dueño)';
    setErrores(er);
    if (Object.keys(er).length) return;
    setGuardando(true);
    setError('');
    try {
      await editarPlanAdmin(plan.idPlan, {
        precioUsd: Number(v.precioUsd),
        maxProductos: v.ilimitado ? null : Number(v.maxProductos),
        comision: Number(v.comision),
        permitePersonalizar: v.permitePersonalizar,
        permitePlantillas: v.permitePlantillas,
        permiteMasMetodosPago: v.permiteMasMetodosPago,
        permiteEnvioInternacional: v.permiteEnvioInternacional,
        maxEmpleados: Number(v.maxEmpleados),
      });
      avisar(`Plan ${plan.nombre} actualizado`);
    } catch (ex) {
      if (ex.response?.data?.campos) setErrores(ex.response.data.campos);
      else setError(mensajeDeError(ex));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={guardar} noValidate className={`${TARJETA} space-y-4 p-5 text-[13px]`}>
      <h2 className="text-lg font-bold text-[#101010]">{plan.nombre}</h2>
      {error && <Mensaje tipo="error" texto={error} />}
      <label className="block">
        <span className="text-[#303030]">Precio al mes (US$)</span>
        <input
          inputMode="decimal"
          value={v.precioUsd}
          disabled={gratis}
          onChange={(e) => cambiar('precioUsd', e.target.value)}
          className={`${inputPanel(errores.precioUsd)} mt-1 disabled:bg-[#f7f7f7]`}
        />
        {errores.precioUsd && <span className="mt-1 block text-[12px] text-[#8e1f0b]">{errores.precioUsd}</span>}
      </label>
      <div>
        <span className="text-[#303030]">Productos</span>
        <div className="mt-1 flex items-center gap-3">
          <input
            inputMode="numeric"
            value={v.maxProductos}
            disabled={v.ilimitado}
            onChange={(e) => cambiar('maxProductos', e.target.value)}
            placeholder="Ilimitados"
            className={`${inputPanel(errores.maxProductos)} !w-28 disabled:bg-[#f7f7f7]`}
          />
          <label className="flex items-center gap-1.5">
            <input type="checkbox" checked={v.ilimitado} onChange={(e) => cambiar('ilimitado', e.target.checked)} />
            Ilimitados
          </label>
        </div>
        {errores.maxProductos && <span className="mt-1 block text-[12px] text-[#8e1f0b]">{errores.maxProductos}</span>}
      </div>
      <label className="block">
        <span className="text-[#303030]">Empleados por tienda (0 = solo el dueño)</span>
        <input
          inputMode="numeric"
          value={v.maxEmpleados}
          onChange={(e) => cambiar('maxEmpleados', e.target.value)}
          className={`${inputPanel(errores.maxEmpleados)} mt-1 !w-28`}
        />
        {errores.maxEmpleados && <span className="mt-1 block text-[12px] text-[#8e1f0b]">{errores.maxEmpleados}</span>}
      </label>
      <label className="block">
        <span className="text-[#303030]">Comisión de Vendely por venta (%)</span>
        <input
          inputMode="decimal"
          value={v.comision}
          onChange={(e) => cambiar('comision', e.target.value)}
          className={`${inputPanel(errores.comision)} mt-1`}
        />
        {errores.comision && <span className="mt-1 block text-[12px] text-[#8e1f0b]">{errores.comision}</span>}
      </label>
      <fieldset className="space-y-2">
        <legend className="mb-1 text-[#303030]">Incluye</legend>
        {BENEFICIOS.map(([campo, texto]) => (
          <label key={campo} className="flex items-start gap-2">
            <input type="checkbox" className="mt-0.5" checked={v[campo]} onChange={(e) => cambiar(campo, e.target.checked)} />
            <span>{texto}</span>
          </label>
        ))}
      </fieldset>
      <div className="flex justify-end">
        <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Guardando...' : 'Guardar cambios'}</button>
      </div>
    </form>
  );
}
