import type { SiteConfig } from "@luciodale/docs-ui-kit/types/config";

export const siteConfig: SiteConfig = {
	title: "fork",
	description:
		"A form library for Reagent & Re-frame. Handles the form plumbing you don't want to build: state orchestration, pluggable validation, field arrays, and server request handling. Your components, your validation, your style.",
	siteUrl: "https://koolcodez.com/projects/fork",
	logoSrc: "/logo.svg",
	logoAlt: "fork logo",
	ogImage: "/og-image.png",
	installCommand: 'fork {:mvn/version "2.4.3"}',
	githubUrl: "https://github.com/luciodale/fork",
	author: "Lucio D'Alessandro",
	socialLinks: {
		github: "https://github.com/luciodale",
		linkedin: "https://www.linkedin.com/in/luciodale",
	},
	navLinks: [
		{ href: "/docs/getting-started", label: "Docs" },
		{ href: "/docs/examples/datepicker", label: "Examples" },
	],
	sidebarSections: [
		{
			title: "Getting Started",
			links: [
				{ href: "/docs/getting-started", label: "Introduction" },
				{ href: "/docs/configuration", label: "Configuration" },
				{ href: "/docs/api", label: "API Reference" },
			],
		},
		{
			title: "Guides",
			links: [
				{ href: "/docs/validation", label: "Validation" },
				{ href: "/docs/field-arrays", label: "Field Arrays" },
				{ href: "/docs/server-requests", label: "Server Requests" },
			],
		},
		{
			title: "Examples",
			links: [
				{ href: "/docs/examples/datepicker", label: "Datepicker" },
				{
					href: "/docs/examples/nested-field-arrays",
					label: "Nested Field Arrays",
				},
				{
					href: "/docs/examples/multi-select",
					label: "Multi-Select Dropdown",
				},
			],
		},
	],
	copyright: "Lucio D'Alessandro",
	parentSite: {
		href: "https://koolcodez.com/projects",
		label: "koolcodez",
		logoSrc: "/kool-codez-illustration.svg",
	},
};
