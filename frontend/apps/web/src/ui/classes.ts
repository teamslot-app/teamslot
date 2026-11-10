/** Joint des classes en ignorant les valeurs fausses : classes('a', estActif && 'b'). */
export function classes(
  ...liste: Array<string | false | null | undefined>
): string {
  return liste.filter(Boolean).join(' ')
}
