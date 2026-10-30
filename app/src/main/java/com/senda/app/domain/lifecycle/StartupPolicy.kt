package com.senda.app.domain.lifecycle

/**
 * Decide se os serviços do Senda devem ser iniciados depois de um reboot
 * ou de uma atualização do app.
 *
 * Sem o serviço de acessibilidade ligado não há como detectar a abertura de
 * aplicativos, então subir o serviço de primeiro plano só consumiria bateria.
 */
object StartupPolicy {

    fun shouldStartServices(accessibilityEnabled: Boolean): Boolean = accessibilityEnabled
}
