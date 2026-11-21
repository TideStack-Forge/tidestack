import React from 'react'
import { renderAmisSchema } from 'ouroboros-sdk'

class DevKeyControlButton extends React.Component {
  constructor(props) {
    super(props)
    this.devKeyButtonRef = React.createRef()
    this.renderHandle = null
  }

  componentDidMount() {
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

    if (!this.props.appName) {
      if (this.devKeyButtonRef.current) {
        this.devKeyButtonRef.current.innerHTML = ''
      }
      return
    }

    const schema = {
      type: 'amis-schema',
      schemaId: '/dev/dev-key-control-button',
    }
    this.renderHandle = await renderAmisSchema(this.devKeyButtonRef.current, schema, {
      data: {
        appName: this.props.appName,
      },
    })
  }

  render() {
    return (
      <div>
        <div ref={this.devKeyButtonRef}></div>
      </div>
    )
  }
}

export default DevKeyControlButton
