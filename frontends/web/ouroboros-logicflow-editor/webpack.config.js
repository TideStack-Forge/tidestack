'use strict'

const path = require('path')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const HtmlWebpackPlugin = require('html-webpack-plugin')

const packageName = require('./package.json').name
const port = process.env.port || process.env.npm_config_port || 8082
const { editorBuild } = require('./scripts/editor-build.cjs')

function resolve(dir) {
  return path.join(__dirname, dir)
}

module.exports = (_env, argv = {}) => {
  const isProduction = argv.mode === 'production'
  const build = editorBuild(__dirname, {
    production: isProduction,
    buildId: process.env.OUROBOROS_EDITOR_BUILD_ID,
    publicPath: process.env.OUROBOROS_EDITOR_PUBLIC_PATH,
  })
  const publicPath = build.publicPath
  const ideBundle = publicPath === 'auto'

  return {
    mode: isProduction ? 'production' : 'development',
    entry: {
      app: resolve('src/main.js'),
    },
    output: {
      path: resolve('dist'),
      filename: ideBundle ? 'static/js/[name].js' : 'static/js/[name].[contenthash:12].js',
      chunkFilename: 'static/js/[name].[contenthash:12].js',
      publicPath,
      clean: true,
      library: `${packageName}-[name]`,
      libraryTarget: 'umd',
      globalObject: 'self',
      assetModuleFilename: 'static/assets/[contenthash][ext][query]',
    },
    devtool: isProduction ? false : 'eval-cheap-module-source-map',
    resolve: {
      extensions: ['.js', '.jsx', '.mjs', '.ts', '.tsx', '.json'],
      alias: {
        'ouroboros-sdk': require('node:path').resolve(
          __dirname,
          '../ouroboros-sdk/src/main.ts'
        ),
        '@': resolve('src'),
      },
    },
    module: {
      rules: [
        {
          test: /\.m?js$/,
          resolve: {
            fullySpecified: false,
          },
        },
        {
          test: /\.(js|jsx|mjs|ts|tsx)$/,
          exclude: /node_modules/,
          use: {
            loader: 'babel-loader',
          },
        },
        {
          test: /\.css$/,
          use: [isProduction ? MiniCssExtractPlugin.loader : 'style-loader', 'css-loader'],
        },
        {
          test: /\.s[ac]ss$/,
          use: [
            isProduction ? MiniCssExtractPlugin.loader : 'style-loader',
            'css-loader',
            'sass-loader',
          ],
        },
        {
          test: /\.svg$/,
          include: [resolve('src/icons/svg')],
          use: [
            {
              loader: 'svg-sprite-loader',
              options: {
                symbolId: 'icon-[name]',
              },
            },
          ],
        },
        {
          test: /\.(png|jpe?g|gif|svg|webp|woff2?|eot|ttf|otf)$/i,
          exclude: [resolve('src/icons/svg')],
          type: 'asset/resource',
        },
      ],
    },
    plugins: [
      {
        apply(compiler) {
          compiler.hooks.thisCompilation.tap('EditorBuild', (compilation) => {
            compilation.hooks.processAssets.tap({
              name: 'EditorBuild',
              stage: compiler.webpack.Compilation.PROCESS_ASSETS_STAGE_SUMMARIZE,
            }, () => compilation.emitAsset('editor-build.json',
              new compiler.webpack.sources.RawSource(JSON.stringify(build, null, 2))))
          })
        },
      },
      new HtmlWebpackPlugin({
        template: resolve('public/index.html'),
      }),
      new MiniCssExtractPlugin({
        filename: ideBundle ? 'static/css/[name].css' : 'static/css/[name].[contenthash:12].css',
        chunkFilename: 'static/css/[name].[contenthash:12].css',
      }),
    ],
    externals: isProduction
      ? {
          react: 'React',
          'react-dom': 'ReactDOM',
        }
      : {},
    optimization: {
      splitChunks: false,
      runtimeChunk: false,
    },
    devServer: {
      port,
      open: false,
      headers: {
        'Access-Control-Allow-Origin': '*',
      },
      static: {
        directory: resolve('dist'),
        publicPath,
        watch: false,
      },
      devMiddleware: {
        publicPath,
      },
    },
    performance: {
      hints: false,
    },
    stats: 'minimal',
  }
}
