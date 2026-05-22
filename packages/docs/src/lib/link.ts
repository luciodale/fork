function ensureTrailingSlash(path: string): string {
	if (!path) return path;
	const match = path.match(/^([^?#]*)([?#].*)?$/);
	if (!match) return path;
	const main = match[1] ?? "";
	const rest = match[2] ?? "";
	if (!main || main.endsWith("/")) return path;
	const lastSegment = main.split("/").pop() ?? "";
	if (lastSegment.includes(".")) return path;
	return `${main}/${rest}`;
}

/**
 * Prepends the Astro base path to an internal href and ensures a trailing
 * slash on route paths (skipped for file paths, anchor-only, and external
 * URLs). Use this for all hardcoded `<a href>` in .astro templates.
 */
export function href(path: string): string {
	if (path.startsWith("http://") || path.startsWith("https://")) return path;
	const withSlash = ensureTrailingSlash(path);
	const base = (import.meta.env.BASE_URL ?? "/").replace(/\/$/, "");
	if (!base || base === "/") return withSlash;
	return `${base}${withSlash.startsWith("/") ? withSlash : `/${withSlash}`}`;
}
