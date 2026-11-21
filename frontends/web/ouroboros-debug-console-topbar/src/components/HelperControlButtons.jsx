import React from 'react'
import { renderAmisSchema } from 'ouroboros-sdk'

class HelperControlButtons extends React.Component {
  constructor(props) {
    super(props)
    this.helperBtnsRef = React.createRef()
    this.renderHandle = null
  }
  async componentDidMount() {
    this.renderContent()
  }
  async componentDidUpdate(prevProps) {
    if (prevProps.appName !== this.props.appName) {
      await this.renderContent()
    }
  }
  componentWillUnmount() {
    this.renderHandle?.destroy?.()
    this.renderHandle = null
  }

  async renderContent() {
    this.renderHandle?.destroy?.()
    this.renderHandle = null
    const schema = {
      type: 'amis-schema',
      schemaId: '/dev/helper-control-buttons',
    }
    this.renderHandle = await renderAmisSchema(this.helperBtnsRef.current, schema, {
      data: {
        appName: this.props.appName || '',
      },
    })
  }
  render() {
    return (
      <div>
        <div ref={this.helperBtnsRef}></div>
      </div>
    )
  }
}
export default HelperControlButtons
