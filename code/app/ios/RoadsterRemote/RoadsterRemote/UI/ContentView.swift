//
//  ContentView.swift
//  RoadsterRemote
//
//  Created by Jeffrey Thompson on 2/7/26.
//

import SwiftUI

struct ContentView: View {

    enum Screen {
        case carPicker
        case driving
    }

    @State var controller = CentralControl()
    @State var screen: Screen = .carPicker
    
    let grad: LinearGradient = .init(gradient: .init(colors: [.init(white: 0.2), .init(white: 0.4)]), startPoint: .bottom, endPoint: .top)
    
    var body: some View {
        ZStack {
            grad
                .ignoresSafeArea()
            HStack {
                GearView(gear: $controller.gear)
                    .shadow(radius: 3.0)
                
                VStack {
                    FuelGaugeView(batteryLevel: controller.batteryLevel, isCharging: controller.isOnUsbPower)
                        .shadow(color: .black, radius: 3.0)
                    
                    HStack {
                        HeadlightControlView(isOn: $controller.headlight)
                            .padding()
                            .shadow(color: .black, radius: 2.0)
                        Button {
                            screen = .carPicker
                        } label: {
                            ConnectionView(connected: controller.isConnected)
                        }
                        .shadow(color: .black, radius: 1.0)
                        .accessibilityLabel(controller.isConnected ? "Connected. Change car" : "Not connected. Select car")
                        .padding()
                    }
                    .padding(15)
                }
                .padding(15)
                .foregroundStyle(Color.white)
                
                ThrottleView(throttle: $controller.throttle)
                    .frame(width: 165)
                    .clipShape(RoundedRectangle(cornerRadius: 25))
                    .shadow(color: .black, radius: 2.0)
            }
            .padding(15)
        }
        .sheet(isPresented: isShowingCarPicker) {
            CarPickerView(controller: controller)
        }
        .onChange(of: controller.connectionState) { _, state in
            // Lost the car mid-drive: go straight back to the search list
            if case .lost = state {
                screen = .carPicker
            }
        }
    }

    private var isShowingCarPicker: Binding<Bool> {
        Binding(
            get: { screen == .carPicker },
            set: { screen = $0 ? .carPicker : .driving }
        )
    }
}

#Preview {
    ContentView(controller: CentralControl(), screen: .driving)
}
