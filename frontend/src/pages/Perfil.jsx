import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import CampoPanel from '../components/CampoPanel';
import { IconoCandado, IconoEnlaceExterno } from '../components/Iconos';
import { AvatarTienda } from '../components/MenuCuenta';
import Mensaje from '../components/Mensaje';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { guardarPerfil, obtenerPerfil } from '../services/perfil';
import { formatoFecha } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, TARJETA, etiqueta, inputPanel } from '../utils/panel';

const NOMBRE_ROL = {
  ADMIN_TIENDA: 'Dueño de tienda',
  EMPLEADO: 'Empleado',
  SUPERADMIN: 'Administrador de Vendely',
};

const TELEFONO = /^\+[1-9][0-9]{6,14}$/;

function datosDelFormulario(p) {
  return {
    nombres: p.nombres ?? '',
    primerApellido: p.primerApellido ?? '',
    segundoApellido: p.segundoApellido ?? '',
    telefono: p.telefono ?? '',
    razonSocial: p.negocio?.razonSocial ?? '',
    telefonoComercial: p.negocio?.telefonoComercial ?? '',
  };
}

/**
 * Mi perfil: los datos de la cuenta (nombre, teléfono, correo, rol), los del negocio si es dueño
 * (documento y razón social), el estado de la seguridad y las tiendas en las que trabaja.
 */
export default function Perfil() {
  const { usuario, actualizarUsuario } = useAuth();
  const { avisar } = useToast();
  const esPanelTienda = usuario.rol === 'ADMIN_TIENDA' || usuario.rol === 'EMPLEADO';
  const { tiendas, tienda, cambiarTienda } = useTiendaActual(esPanelTienda);
  const [perfil, setPerfil] = useState(null);
  const [d, setD] = useState(null);
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    let vigente = true;
    obtenerPerfil()
      .then((p) => {
        if (!vigente) return;
        setPerfil(p);
        setD(datosDelFormulario(p));
      })
      .catch((e) => vigente && setError(mensajeDeError(e)));
    return () => {
      vigente = false;
    };
  }, []);

  if (!perfil || !d) {
    return error ? <Mensaje tipo="error" texto={error} /> : <p className="text-[13px] text-[#616161]">Cargando tu perfil...</p>;
  }

  const cambiado = JSON.stringify(d) !== JSON.stringify(datosDelFormulario(perfil));
  const cambiar = (campo) => (e) => setD((v) => ({ ...v, [campo]: e.target.value }));

  async function guardar(e) {
    e.preventDefault();
    const er = {};
    if (!d.nombres.trim()) er.nombres = 'Los nombres son obligatorios';
    if (!d.primerApellido.trim()) er.primerApellido = 'El primer apellido es obligatorio';
    if (d.telefono.trim() && !TELEFONO.test(d.telefono.trim())) er.telefono = 'Formato internacional, por ejemplo +51987654321';
    if (d.telefonoComercial.trim() && !TELEFONO.test(d.telefonoComercial.trim())) {
      er.telefonoComercial = 'Formato internacional, por ejemplo +51987654321';
    }
    setErrores(er);
    if (Object.keys(er).length) return;
    setGuardando(true);
    setError('');
    try {
      const r = await guardarPerfil({
        nombres: d.nombres.trim(),
        primerApellido: d.primerApellido.trim(),
        segundoApellido: d.segundoApellido.trim(),
        telefono: d.telefono.trim(),
        razonSocial: d.razonSocial.trim(),
        telefonoComercial: d.telefonoComercial.trim(),
      });
      setPerfil(r.perfil);
      setD(datosDelFormulario(r.perfil));
      actualizarUsuario(r.usuario);   // el nombre de arriba y del menú se actualiza al instante
      avisar('Perfil guardado');
    } catch (ex) {
      if (ex.response?.data?.campos) setErrores(ex.response.data.campos);
      else setError(mensajeDeError(ex));
    } finally {
      setGuardando(false);
    }
  }

  const nombreCompleto = [perfil.nombres, perfil.primerApellido, perfil.segundoApellido].filter(Boolean).join(' ');

  return (
    <section className="space-y-4">
      {/* Encabezado: quién es */}
      <div className="flex flex-wrap items-center gap-4">
        <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[var(--v-mango)] text-2xl font-bold text-[var(--v-tinta)]">
          {(perfil.nombres ?? '?').trim().charAt(0).toUpperCase()}
        </span>
        <div className="min-w-0">
          <h1 className="truncate text-xl font-bold text-[#101010]">{nombreCompleto}</h1>
          <p className="text-[13px] text-[#616161]">
            <span className={etiqueta('info')}>{NOMBRE_ROL[perfil.rol] ?? perfil.rol}</span>
            <span className="ml-2">{perfil.correo}</span>
            {perfil.fechaCreacion && <span className="ml-2">· En Vendely desde {formatoFecha(perfil.fechaCreacion).split(',')[0]}</span>}
          </p>
        </div>
      </div>

      {error && <Mensaje tipo="error" texto={error} onCerrar={() => setError('')} />}

      <div className="grid gap-4 lg:grid-cols-[1fr_340px]">
        <form onSubmit={guardar} noValidate className="space-y-4">
          <div className={`${TARJETA} space-y-3 p-5 text-[13px]`}>
            <h2 className="font-semibold text-[#101010]">Datos personales</h2>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              <CampoPanel etiqueta="Nombres" error={errores.nombres}>
                <input value={d.nombres} onChange={cambiar('nombres')} autoComplete="given-name" className={inputPanel(errores.nombres)} />
              </CampoPanel>
              <CampoPanel etiqueta="Primer apellido" error={errores.primerApellido}>
                <input value={d.primerApellido} onChange={cambiar('primerApellido')} autoComplete="family-name" className={inputPanel(errores.primerApellido)} />
              </CampoPanel>
              <CampoPanel etiqueta="Segundo apellido (opcional)" error={errores.segundoApellido}>
                <input value={d.segundoApellido} onChange={cambiar('segundoApellido')} className={inputPanel(errores.segundoApellido)} />
              </CampoPanel>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <CampoPanel etiqueta="Teléfono (opcional)" error={errores.telefono} ayuda="Con código de país: +51...">
                <input type="tel" value={d.telefono} onChange={cambiar('telefono')} placeholder="+51987654321" autoComplete="tel" className={inputPanel(errores.telefono)} />
              </CampoPanel>
              <CampoPanel etiqueta="Correo" ayuda="Es tu usuario para entrar; no se cambia desde aquí.">
                <input value={perfil.correo} disabled className={`${inputPanel()} bg-[#f7f7f7] text-[#616161]`} />
              </CampoPanel>
            </div>
          </div>

          {perfil.negocio && (
            <div className={`${TARJETA} space-y-3 p-5 text-[13px]`}>
              <h2 className="font-semibold text-[#101010]">Datos del negocio</h2>
              <div className="grid gap-3 sm:grid-cols-2">
                <CampoPanel etiqueta={perfil.negocio.nombreDocumento ?? 'Documento'} ayuda="Identifica al dueño; no se cambia desde aquí.">
                  <input
                    value={`${perfil.negocio.tipoDocumento ?? ''} ${perfil.negocio.numeroDocumento}`.trim()}
                    disabled
                    className={`${inputPanel()} bg-[#f7f7f7] text-[#616161]`}
                  />
                </CampoPanel>
                <CampoPanel etiqueta="Razón social (opcional)" error={errores.razonSocial}>
                  <input value={d.razonSocial} onChange={cambiar('razonSocial')} placeholder="Mi Empresa S.A.C." className={inputPanel(errores.razonSocial)} />
                </CampoPanel>
                <CampoPanel etiqueta="Teléfono del negocio (opcional)" error={errores.telefonoComercial}>
                  <input type="tel" value={d.telefonoComercial} onChange={cambiar('telefonoComercial')} placeholder="+51987654321" className={inputPanel(errores.telefonoComercial)} />
                </CampoPanel>
              </div>
            </div>
          )}

          <div className="flex justify-end gap-2">
            <button type="button" disabled={!cambiado || guardando} onClick={() => { setD(datosDelFormulario(perfil)); setErrores({}); }} className={BOTON_SECUNDARIO}>
              Descartar
            </button>
            <button type="submit" disabled={!cambiado || guardando} className={BOTON}>
              {guardando ? 'Guardando...' : 'Guardar cambios'}
            </button>
          </div>
        </form>

        <div className="space-y-4">
          <div className={`${TARJETA} space-y-3 p-5 text-[13px]`}>
            <h2 className="flex items-center gap-2 font-semibold text-[#101010]"><IconoCandado tamano={17} /> Seguridad</h2>
            <dl className="space-y-2">
              <div className="flex justify-between gap-3">
                <dt className="text-[#616161]">Contraseña</dt>
                <dd className="text-right">
                  {perfil.contrasenaCambiadaEn ? `Cambiada el ${formatoFecha(perfil.contrasenaCambiadaEn).split(',')[0]}` : 'La de tu registro'}
                </dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-[#616161]">Doble autenticación</dt>
                <dd>
                  <span className={etiqueta(perfil.dobleFactor ? 'exito' : 'aviso')}>
                    {perfil.dobleFactor ? 'Activada' : 'Desactivada'}
                  </span>
                </dd>
              </div>
            </dl>
            <Link to="/cuenta/seguridad" className={`${BOTON_SECUNDARIO} inline-block`}>Cambiar contraseña o doble autenticación</Link>
          </div>

          {esPanelTienda && (
            <div className={`${TARJETA} p-5 text-[13px]`}>
              <h2 className="mb-3 font-semibold text-[#101010]">Tus tiendas</h2>
              {tiendas.length === 0 && <p className="text-[#616161]">Todavía no tienes tiendas.</p>}
              <ul className="space-y-2">
                {tiendas.map((t) => (
                  <li key={t.idTienda} className="flex items-center gap-3">
                    <AvatarTienda tienda={t} tamano={32} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium text-[#101010]">{t.nombre}</p>
                      <p className="truncate text-xs text-[#616161]">
                        {t.esDueno === false ? 'Empleado' : `Plan ${t.plan ?? 'Gratis'}`} · {t.pais}
                        {t.estado === 'SUSPENDIDA' ? ' · Suspendida' : ''}
                      </p>
                    </div>
                    <a
                      href={`${window.location.origin}/tienda/${t.subdominio}`}
                      target="_blank"
                      rel="noreferrer"
                      aria-label={`Ver la tienda ${t.nombre}`}
                      className="rounded-lg p-1.5 text-[#616161] hover:bg-[#f1f1f1]"
                    >
                      <IconoEnlaceExterno tamano={15} />
                    </a>
                    {t.idTienda === tienda?.idTienda ? (
                      <span className={etiqueta('exito')}>Actual</span>
                    ) : (
                      <button type="button" onClick={() => { cambiarTienda(t.idTienda); avisar(`Ahora trabajas en ${t.nombre}`); }} className="rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">
                        Cambiar
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
