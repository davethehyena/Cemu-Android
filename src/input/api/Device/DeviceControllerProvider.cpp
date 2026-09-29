#include "DeviceControllerProvider.h"
#include "DeviceController.h"
#include "config/CemuConfig.h"

std::vector<std::shared_ptr<ControllerBase>> DeviceControllerProvider::get_controllers()
{
	// El interruptor de motion del mando nace en false y use_motion() es
	// has_motion() && m_settings.motion, asi que sin esto el giroscopio del
	// movil se leia y se procesaba pero el juego creia que no habia sensor.
	// Splatoon se quedaba bloqueado en la pantalla de inicio por eso.
	// Se enciende aqui, al crear el mando, y no al arrancar un juego: el
	// provider se registra al inicio de Cemu y hacerlo despues dejaria el flag
	// en false durante toda la sesion.
	auto controller = std::make_shared<DeviceController>();
	controller->set_use_motion(GetConfig().device_motion);
	return {std::move(controller)};
}
