import { SetMetadata } from '@nestjs/common';

export const IS_PUBLIC = 'paycore:is-public';

/**
 * Marca rotas que não exigem API key (health e documentação).
 */
export const Public = () => SetMetadata(IS_PUBLIC, true);
