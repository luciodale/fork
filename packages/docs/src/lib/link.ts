/**
 * Prepends the Astro base path to an internal href.
 * Use this for all hardcoded `<a href>` in .astro templates.
 */
export function href(path: string): string {
	const base = (import.meta.env.BASE_URL ?? "/").replace(/\/$/, "");
	if (!base || base === "/") return path;
	return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}
