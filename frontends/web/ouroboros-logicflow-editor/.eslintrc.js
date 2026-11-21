module.exports = {
  root: true,
  parser: '@babel/eslint-parser',
  env: {
    browser: true,
    node: true,
    es2021: true,
  },
  globals: {
    expect: 'readonly',
    test: 'readonly',
  },
  settings: {
    react: {
      version: 'detect',
    },
  },
  parserOptions: {
    requireConfigFile: false,
    sourceType: 'module',
    ecmaFeatures: {
      jsx: true,
    },
    babelOptions: {
      parserOpts: {
        plugins: [
          'jsx',
          'classProperties',
          'classPrivateProperties',
          'classPrivateMethods',
          'optionalChaining',
          'nullishCoalescingOperator',
        ],
      },
    },
  },
  extends: ['eslint:recommended', 'plugin:react/recommended'],
  plugins: ['react'],
  rules: {
    'brace-style': 'off',
    'comma-dangle': [
      2,
      {
        arrays: 'always-multiline',
        objects: 'always-multiline',
        imports: 'always-multiline',
        exports: 'always-multiline',
        functions: 'never',
      },
    ],
    eqeqeq: 'off',
    indent: 'off',
    'no-console': 'off',
    'no-debugger': process.env.NODE_ENV === 'production' ? 2 : 0,
    'no-multiple-empty-lines': 'off',
    'no-trailing-spaces': 'off',
    'no-unneeded-ternary': 'off',
    'no-unsafe-optional-chaining': 'off',
    'no-unused-vars': [
      2,
      {
        args: 'all',
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
      },
    ],
    'react/prop-types': 'off',
    'react/react-in-jsx-scope': 'off',
    'object-curly-spacing': 'off',
    'operator-linebreak': 'off',
    'prefer-const': 'off',
    semi: 'off',
    'space-before-function-paren': 'off',
    'spaced-comment': 'off',
  },
}
