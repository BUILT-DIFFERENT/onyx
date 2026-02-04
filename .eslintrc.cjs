module.exports = {
  root: true,
  env: {
    es2022: true,
    browser: true,
    node: true,
  },
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaVersion: "latest",
    sourceType: "module",
  },
  plugins: ["@typescript-eslint"],
  extends: ["eslint:recommended", "plugin:@typescript-eslint/recommended"],
  ignorePatterns: [
    "node_modules/",
    ".bun/",
    ".turbo/",
    "dist/",
    "build/",
    "coverage/",
    "playwright-report/",
    "test-results/",
  ],
  overrides: [
    {
      files: ["apps/web/**/*.{js,jsx,ts,tsx}"],
      plugins: ["react", "react-hooks", "tailwindcss"],
      extends: [
        "plugin:react/recommended",
        "plugin:react-hooks/recommended",
        "plugin:tailwindcss/recommended",
      ],
      settings: {
        react: {
          version: "detect",
        },
      },
    },
  ],
};
