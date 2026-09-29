#include "DeviceControllerProvider.h"
#include "DeviceController.h"

std::vector<std::shared_ptr<ControllerBase>> DeviceControllerProvider::get_controllers()
{
	// El interruptor de motion del mando nace en false y use_motion() es
	// has_motion() && m_settings.motion. El movil siempre trae acelerometro y
	// giroscopio, asi que se enciende aqui, al registrar el provider: si se
	// hiciera al arrancar un juego, el mando ya estaria creado y el flag se
	// quedaria en false para toda la sesion.
	auto controller = std::make_shared<DeviceController>();
	controller->set_use_motion(true);
	return {std::move(controller)};
}
