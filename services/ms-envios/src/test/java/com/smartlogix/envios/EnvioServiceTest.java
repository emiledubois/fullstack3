package com.smartlogix.envios;

import com.smartlogix.envios.dto.CreateEnvioRequest;
import com.smartlogix.envios.factory.EnvioFactory;
import com.smartlogix.envios.model.Envio;
import com.smartlogix.envios.repository.EnvioRepository;
import com.smartlogix.envios.service.EnvioService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvioServiceTest {

    @Mock private EnvioRepository envioRepository;
    @Mock private EnvioFactory    envioFactory;
    @InjectMocks private EnvioService envioService;

    @Test @DisplayName("crearEnvio usa factory y persiste el envío")
    void crearEnvio_persistsCorrectly() {
        CreateEnvioRequest req = new CreateEnvioRequest();
        req.setPedidoId(1L); req.setTipoEnvio("TERRESTRE");
        req.setTransportista("Chilexpress"); req.setDestino("Santiago");

        Envio envio = Envio.builder().pedidoId(1L).tipoEnvio("TERRESTRE").build();
        Envio saved = Envio.builder().id(1L).pedidoId(1L).tipoEnvio("TERRESTRE").status("CREADO").build();
        when(envioFactory.crearEnvio(req)).thenReturn(envio);
        when(envioRepository.save(envio)).thenReturn(saved);

        assertThat(envioService.crearEnvio(req).getStatus()).isEqualTo("CREADO");
        verify(envioFactory).crearEnvio(req);
        verify(envioRepository).save(envio);
    }

    @Test @DisplayName("getById lanza excepción si el envío no existe")
    void getById_throwsWhenNotFound() {
        when(envioRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> envioService.getById(999L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Envío no encontrado");
    }

    @Test @DisplayName("getPorPedidoIds con pedidoIds existentes retorna los envíos mapeados")
    void getPorPedidoIds_conPedidoIdsExistentes_retornaListaMapeada() {
        Envio envio = Envio.builder().id(17L).pedidoId(42L).status("ENTREGADO")
            .tipoEnvio("TERRESTRE").transportista("Transportes del Sur").destino("Valparaíso").build();
        when(envioRepository.findByPedidoIdIn(List.of(42L))).thenReturn(List.of(envio));

        List<com.smartlogix.envios.dto.EnvioDatosDTO> resultado = envioService.getPorPedidoIds(List.of(42L));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getId()).isEqualTo(17L);
        assertThat(resultado.get(0).getPedidoId()).isEqualTo(42L);
    }

    @Test @DisplayName("getPorPedidoIds con lista vacía retorna vacío sin consultar el repositorio")
    void getPorPedidoIds_listaVacia_retornaVacioSinConsultarRepositorio() {
        List<com.smartlogix.envios.dto.EnvioDatosDTO> resultado = envioService.getPorPedidoIds(List.of());

        assertThat(resultado).isEmpty();
        verify(envioRepository, never()).findByPedidoIdIn(any());
    }
}
