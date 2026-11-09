import { createHmac } from 'node:crypto';
import { Injectable } from '@nestjs/common';

/**
 * Assina o corpo exato enviado ao parceiro. O timestamp entra na mensagem
 * assinada para permitir que o receptor rejeite replay fora da janela.
 */
@Injectable()
export class WebhookSigner {
  signature(secret: string, timestamp: number, body: string): string {
    const digest = createHmac('sha256', secret)
      .update(`${timestamp}.${body}`, 'utf8')
      .digest('hex');
    return `v1=${digest}`;
  }
}
