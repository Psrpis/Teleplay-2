module.exports = {
  root: true,
  parser: '@typescript-eslint/parser',
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module', ecmaFeatures: { jsx: true } },
  env: { browser: true, es2022: true, node: true },
  plugins: ['@typescript-eslint', 'react-hooks', 'react-refresh'],
  extends: [],
  rules: {
    'no-console': 'off',
    'no-debugger': 'error',
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'off',
    'react-refresh/only-export-components': 'off',
    '@typescript-eslint/no-explicit-any': 'off',
  },
};
