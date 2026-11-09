import { lookup } from 'node:dns/promises';
import { Inject, Injectable } from '@nestjs/common';
import ipaddr from 'ipaddr.js';
import { APP_CONFIG, AppConfig } from '../../config/app-config';

export class BlockedDestinationError extends Error {
  constructor(readonly reason: string) {
    super(`destino de webhook bloqueado: ${reason}`);
    this.name = 'BlockedDestinationError';
  }
}

/**
 * Barreira de SSRF aplicada no momento da entrega. O cadastro já exige http(s)
 * sem credenciais; aqui o host é resolvido e todos os endereços precisam ser
 * unicast público. O laboratório pode liberar redes privadas explicitamente.
 */
@Injectable()
export class WebhookDestinationGuard {
  constructor(@Inject(APP_CONFIG) private readonly config: AppConfig) {}

  async assertAllowed(url: string, timeoutMs: number): Promise<void> {
    let parsed: URL;
    try {
      parsed = new URL(url);
    } catch {
      throw new BlockedDestinationError('url_invalida');
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      throw new BlockedDestinationError('esquema_nao_suportado');
    }
    if (parsed.username || parsed.password) {
      throw new BlockedDestinationError('credenciais_na_url');
    }
    if (this.config.webhooks.allowPrivateNetworks) {
      return;
    }
    const hostname = parsed.hostname.replace(/^\[|\]$/g, '');
    const addresses = await withTimeout(
      lookup(hostname, { all: true, verbatim: true }),
      timeoutMs,
    );
    if (addresses.length === 0) {
      throw new BlockedDestinationError('sem_endereco');
    }
    for (const entry of addresses) {
      if (isBlockedAddress(entry.address)) {
        throw new BlockedDestinationError('endereco_privado_ou_reservado');
      }
    }
  }
}

export function isBlockedAddress(value: string): boolean {
  let address: ipaddr.IPv4 | ipaddr.IPv6;
  try {
    address = ipaddr.parse(value);
  } catch {
    return true;
  }
  if (address.kind() === 'ipv6') {
    const ipv6 = address as ipaddr.IPv6;
    if (ipv6.isIPv4MappedAddress()) {
      address = ipv6.toIPv4Address();
    }
  }
  return address.range() !== 'unicast';
}

async function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  let timer: NodeJS.Timeout | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<T>((_resolve, reject) => {
        timer = setTimeout(() => reject(new Error('webhook_destination_timeout')), timeoutMs);
      }),
    ]);
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }
}
